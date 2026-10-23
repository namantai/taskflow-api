const state = {
    tasks: [],
    allTasks: [],
    status: "",
    priority: "",
    search: "",
    loading: false,
    loadError: false,
    summaryLoaded: false,
    editingId: null,
    saving: false,
    taskToDelete: null,
    deleting: false
};

const taskDialog = document.querySelector("#task-dialog");
const deleteDialog = document.querySelector("#delete-dialog");
const form = document.querySelector("#task-form");
const fields = {
    title: document.querySelector("#task-title"),
    description: document.querySelector("#task-description"),
    status: document.querySelector("#task-status"),
    priority: document.querySelector("#task-priority"),
    dueDate: document.querySelector("#task-due-date")
};
let activeLoad;
let notificationTimer;

// All requests use the same origin as the dashboard, so no CORS setup is needed.
async function request(path, options = {}) {
    let response;
    try {
        response = await fetch(path, {
            ...options,
            headers: {
                Accept: "application/json",
                ...(options.body ? { "Content-Type": "application/json" } : {})
            }
        });
    } catch (error) {
        if (error.name === "AbortError") {
            throw error;
        }
        throw new Error("Cannot reach TaskFlow. Check your connection and that the application is running, then try again.");
    }

    if (response.status === 204) {
        return null;
    }
    const data = await response.json().catch(() => null);
    if (!response.ok) {
        let message = data?.detail || "The request could not be completed. Please try again.";
        if (response.status === 404) {
            message = "This task no longer exists. Your task list has been refreshed.";
        } else if (response.status >= 500) {
            message = "TaskFlow encountered a server error. Please try again in a moment.";
        }
        const error = new Error(message);
        error.status = response.status;
        error.fieldErrors = data?.errors || {};
        throw error;
    }
    if (data === null) {
        throw new Error("TaskFlow returned an unreadable response. Please try again.");
    }
    return data;
}

async function loadTasks(refreshSummary = false) {
    // A slower, earlier filter request must not replace a newer result.
    if (refreshSummary) state.summaryLoaded = false;
    activeLoad?.abort();
    const controller = new AbortController();
    activeLoad = controller;
    state.loading = true;
    state.loadError = false;
    renderTasks();

    const params = new URLSearchParams();
    if (state.status) params.set("status", state.status);
    if (state.priority) params.set("priority", state.priority);
    const filtered = params.size > 0;
    const path = filtered ? `/api/tasks?${params}` : "/api/tasks";

    try {
        const [tasks, allTasks] = await Promise.all([
            request(path, { signal: controller.signal }),
            filtered && (refreshSummary || !state.summaryLoaded)
                ? request("/api/tasks", { signal: controller.signal })
                : Promise.resolve(null)
        ]);
        if (controller !== activeLoad) return;
        state.tasks = tasks;
        if (!filtered) state.allTasks = tasks;
        else if (allTasks !== null) state.allTasks = allTasks;
        state.summaryLoaded = true;
        renderSummary();
    } catch (error) {
        if (controller !== activeLoad || error.name === "AbortError") return;
        state.loadError = true;
        document.querySelector("#load-error-message").textContent = error.message;
    } finally {
        if (controller === activeLoad) {
            state.loading = false;
            renderTasks();
        }
    }
}

function renderSummary() {
    document.querySelector("#total-count").textContent = state.allTasks.length;
    document.querySelector("#all-tab-count").textContent = state.allTasks.length;
    document.querySelector("#todo-count").textContent = state.allTasks.filter(task => task.status === "TODO").length;
    document.querySelector("#progress-count").textContent = state.allTasks.filter(task => task.status === "IN_PROGRESS").length;
    document.querySelector("#done-count").textContent = state.allTasks.filter(task => task.status === "DONE").length;
}

function renderTasks() {
    const grid = document.querySelector("#task-grid");
    const query = state.search.trim().toLocaleLowerCase();
    const tasks = state.tasks.filter(task => task.title.toLocaleLowerCase().includes(query));
    const ready = !state.loading && !state.loadError;

    document.querySelector("#loading").hidden = !state.loading;
    document.querySelector("#load-error").hidden = !state.loadError;
    document.querySelector("#refresh").disabled = state.loading;
    document.querySelector("#empty-state").hidden = !ready || tasks.length > 0;
    document.querySelector("#results-count").textContent = ready
        ? `${tasks.length} ${tasks.length === 1 ? "task" : "tasks"} shown` : "";
    grid.hidden = !ready || tasks.length === 0;
    grid.setAttribute("aria-busy", String(state.loading));
    if (!ready) return;

    const hasFilters = Boolean(state.status || state.priority || query);
    document.querySelector("#empty-heading").textContent = hasFilters ? "No matching tasks" : "No tasks yet";
    document.querySelector("#empty-description").textContent = hasFilters
        ? "Try a different search or clear your filters to see more tasks."
        : "Start with one task. The rest will follow.";
    document.querySelector("#empty-action").textContent = hasFilters ? "Clear filters" : "Create your first task";

    const fragment = document.createDocumentFragment();
    for (const task of tasks) {
        fragment.append(createTaskCard(task));
    }
    grid.replaceChildren(fragment);
}

function createTaskCard(task) {
    const card = document.querySelector("#task-template").content.cloneNode(true);
    // Task content is always text, never HTML supplied by the API.
    card.querySelector(".task-title").textContent = task.title;
    card.querySelector(".task-description").textContent = task.description || "No description added.";
    const priority = card.querySelector(".priority-badge");
    priority.textContent = task.priority.charAt(0) + task.priority.slice(1).toLowerCase();
    priority.dataset.priority = task.priority;

    const status = card.querySelector(".card-status");
    status.id = `status-${task.id}`;
    status.value = task.status;
    status.dataset.status = task.status;
    status.setAttribute("aria-label", `Status for ${task.title}`);
    const label = card.querySelector(".task-status-label");
    label.htmlFor = status.id;
    label.textContent = `Status for ${task.title}`;
    status.addEventListener("change", () => changeStatus(task, status));

    const dueDate = card.querySelector(".due-date");
    card.querySelector(".due-label").textContent = task.dueDate ? formatDate(task.dueDate) : "No due date";
    if (task.dueDate && task.dueDate < localDateString() && task.status !== "DONE") {
        dueDate.classList.add("overdue");
        dueDate.title = "Overdue";
    }

    const edit = card.querySelector(".edit-task");
    edit.setAttribute("aria-label", `Edit ${task.title}`);
    edit.addEventListener("click", () => editTask(task.id, edit));
    const remove = card.querySelector(".delete-task");
    remove.setAttribute("aria-label", `Delete ${task.title}`);
    remove.addEventListener("click", () => openDeleteDialog(task));
    return card;
}

function localDateString() {
    const today = new Date();
    return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
}

function formatDate(value) {
    // Local calendar dates must not move a day when converted between time zones.
    return new Intl.DateTimeFormat("en", { month: "short", day: "numeric", year: "numeric" })
        .format(new Date(`${value}T00:00:00`));
}

// PUT replaces every editable field, including the two optional fields.
function editableFields(task) {
    return {
        title: task.title,
        description: task.description,
        status: task.status,
        priority: task.priority,
        dueDate: task.dueDate
    };
}

async function changeStatus(task, select) {
    const newStatus = select.value;
    select.disabled = true;
    try {
        const latest = await request(`/api/tasks/${task.id}`);
        await request(`/api/tasks/${task.id}`, {
            method: "PUT",
            body: JSON.stringify({ ...editableFields(latest), status: newStatus })
        });
        notify("Task status updated.");
        await loadTasks(true);
    } catch (error) {
        select.value = task.status;
        notify(error.message, "error");
        if (error.status === 404) await loadTasks(true);
    } finally {
        select.disabled = false;
    }
}

async function editTask(id, button) {
    button.disabled = true;
    try {
        const task = await request(`/api/tasks/${id}`);
        openTaskDialog(task);
    } catch (error) {
        notify(error.message, "error");
        if (error.status === 404) await loadTasks(true);
    } finally {
        button.disabled = false;
    }
}

function openTaskDialog(task = null) {
    state.editingId = task?.id ?? null;
    form.reset();
    clearFormErrors();
    if (task) {
        for (const [name, input] of Object.entries(fields)) {
            input.value = task[name] ?? "";
        }
    }
    document.querySelector("#task-dialog-heading").textContent = task ? "Edit task" : "Create a task";
    document.querySelector("#task-dialog-description").textContent = task
        ? "A change of plan? Update the details below."
        : "Give your next step a little structure.";
    document.querySelector("#save-task").textContent = task ? "Save Changes" : "Create Task";
    updateDescriptionCount();
    taskDialog.showModal();
    fields.title.focus();
}

function clearFormErrors() {
    document.querySelector("#form-error").hidden = true;
    for (const [name, input] of Object.entries(fields)) {
        input.removeAttribute("aria-invalid");
        document.querySelector(`#${name}-error`).hidden = true;
    }
}

function showFieldErrors(errors) {
    for (const [name, message] of Object.entries(errors)) {
        if (!fields[name]) continue;
        const element = document.querySelector(`#${name}-error`);
        element.textContent = message;
        element.hidden = false;
        fields[name].setAttribute("aria-invalid", "true");
    }
}

function validateTask(task) {
    const errors = {};
    if (task.title.length < 3 || task.title.length > 100) {
        errors.title = "Enter a title between 3 and 100 characters.";
    }
    if (task.description && task.description.length > 2000) {
        errors.description = "Keep the description to 2,000 characters or fewer.";
    }
    if (!["TODO", "IN_PROGRESS", "DONE"].includes(task.status)) errors.status = "Choose a status.";
    if (!["LOW", "MEDIUM", "HIGH"].includes(task.priority)) errors.priority = "Choose a priority.";
    if (fields.dueDate.validity.badInput) errors.dueDate = "Enter a valid due date.";
    return errors;
}

function setSaving(saving) {
    state.saving = saving;
    document.querySelector("#task-fields").disabled = saving;
    document.querySelector("#save-task").disabled = saving;
    document.querySelector("#cancel-task").disabled = saving;
    document.querySelector("#close-task-dialog").disabled = saving;
    document.querySelector("#save-task").textContent = saving
        ? "Saving…" : state.editingId === null ? "Create Task" : "Save Changes";
}

async function saveTask(event) {
    event.preventDefault();
    if (state.saving) return;
    clearFormErrors();
    const task = {
        title: fields.title.value.trim(),
        description: fields.description.value || null,
        status: fields.status.value,
        priority: fields.priority.value,
        dueDate: fields.dueDate.value || null
    };
    const errors = validateTask(task);
    if (Object.keys(errors).length > 0) {
        showFieldErrors(errors);
        form.querySelector('[aria-invalid="true"]').focus();
        return;
    }

    const creating = state.editingId === null;
    setSaving(true);
    try {
        await request(creating ? "/api/tasks" : `/api/tasks/${state.editingId}`, {
            method: creating ? "POST" : "PUT",
            body: JSON.stringify(task)
        });
        taskDialog.close();
        notify(creating ? "Task created." : "Task updated.");
        await loadTasks(true);
    } catch (error) {
        showFieldErrors(error.fieldErrors || {});
        const message = document.querySelector("#form-error");
        message.textContent = error.message;
        message.hidden = false;
        if (error.status === 404) await loadTasks(true);
    } finally {
        setSaving(false);
        form.querySelector('[aria-invalid="true"]')?.focus();
    }
}

function openDeleteDialog(task) {
    state.taskToDelete = task;
    document.querySelector("#delete-description").textContent = `“${task.title}” will be permanently deleted. This can’t be undone.`;
    document.querySelector("#delete-error").hidden = true;
    deleteDialog.showModal();
}

async function deleteTask() {
    if (state.deleting) return;
    state.deleting = true;
    const confirm = document.querySelector("#confirm-delete");
    const cancel = document.querySelector("#cancel-delete");
    confirm.disabled = true;
    cancel.disabled = true;
    confirm.textContent = "Deleting…";
    document.querySelector("#delete-error").hidden = true;
    try {
        await request(`/api/tasks/${state.taskToDelete.id}`, { method: "DELETE" });
        deleteDialog.close();
        notify("Task deleted.");
        await loadTasks(true);
    } catch (error) {
        if (error.status === 404) {
            deleteDialog.close();
            notify(error.message, "error");
            await loadTasks(true);
        } else {
            const message = document.querySelector("#delete-error");
            message.textContent = error.message;
            message.hidden = false;
        }
    } finally {
        state.deleting = false;
        confirm.disabled = false;
        cancel.disabled = false;
        confirm.textContent = "Delete task";
    }
}

function notify(message, type = "success") {
    clearTimeout(notificationTimer);
    const notification = document.querySelector("#notification");
    document.querySelector("#notification-message").textContent = message;
    notification.dataset.type = type;
    notification.hidden = false;
    notificationTimer = setTimeout(() => { notification.hidden = true; }, type === "error" ? 8000 : 4500);
}

function updateDescriptionCount() {
    document.querySelector("#description-count").textContent = `${fields.description.value.length.toLocaleString("en")} / 2,000`;
}

function updateFilterButtons() {
    for (const button of document.querySelectorAll(".filter-button")) {
        const selected = button.dataset.status === state.status;
        button.classList.toggle("selected", selected);
        button.setAttribute("aria-pressed", String(selected));
    }
}

document.querySelector("#today").textContent = new Intl.DateTimeFormat("en", {
    weekday: "short", month: "short", day: "numeric"
}).format(new Date());
document.querySelector("#new-task").addEventListener("click", () => openTaskDialog());
document.querySelector("#close-task-dialog").addEventListener("click", () => taskDialog.close());
document.querySelector("#cancel-task").addEventListener("click", () => taskDialog.close());
document.querySelector("#cancel-delete").addEventListener("click", () => deleteDialog.close());
document.querySelector("#confirm-delete").addEventListener("click", deleteTask);
document.querySelector("#refresh").addEventListener("click", () => loadTasks(true));
document.querySelector("#retry").addEventListener("click", () => loadTasks(true));
document.querySelector("#dismiss-notification").addEventListener("click", () => {
    document.querySelector("#notification").hidden = true;
});
form.addEventListener("submit", saveTask);
fields.description.addEventListener("input", updateDescriptionCount);
taskDialog.addEventListener("cancel", event => { if (state.saving) event.preventDefault(); });
deleteDialog.addEventListener("cancel", event => { if (state.deleting) event.preventDefault(); });

for (const button of document.querySelectorAll(".filter-button")) {
    button.addEventListener("click", () => {
        state.status = button.dataset.status;
        updateFilterButtons();
        loadTasks();
    });
}
document.querySelector("#priority-filter").addEventListener("change", event => {
    state.priority = event.target.value;
    loadTasks();
});
document.querySelector("#search").addEventListener("input", event => {
    state.search = event.target.value;
    renderTasks();
});
document.querySelector("#empty-action").addEventListener("click", () => {
    if (state.status || state.priority || state.search.trim()) {
        state.status = "";
        state.priority = "";
        state.search = "";
        document.querySelector("#priority-filter").value = "";
        document.querySelector("#search").value = "";
        updateFilterButtons();
        loadTasks(true);
    } else {
        openTaskDialog();
    }
});

loadTasks(true);
