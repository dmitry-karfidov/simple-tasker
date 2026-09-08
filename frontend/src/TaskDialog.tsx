import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError, tasksApi, type Task } from './api';
import { formatDate, Icon, StatusBadge } from './ui';

interface Props {
  taskId: number | null;
  onClose: () => void;
  onSaved: (message: string, created: boolean) => void;
}

export default function TaskDialog({ taskId, onClose, onSaved }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [task, setTask] = useState<Task | null>(null);
  const [editing, setEditing] = useState(taskId === null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(taskId !== null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [reload, setReload] = useState(0);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const cancelDeleteButton = useRef<HTMLButtonElement>(null);
  const deleteButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (confirmingDelete) cancelDeleteButton.current?.focus();
    else deleteButton.current?.focus();
  }, [confirmingDelete]);

  useEffect(() => {
    const element = dialog.current!;
    element.showModal();
    return () => element.close();
  }, []);

  useEffect(() => {
    if (taskId === null) return;
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    tasksApi.get(taskId, controller.signal).then((data) => {
      setTask(data);
      setTitle(data.title);
      setDescription(data.description ?? '');
    }).catch((cause: ApiError) => {
      if (!controller.signal.aborted) setError(cause);
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [taskId, reload]);

  async function save(event: FormEvent) {
    event.preventDefault();
    if (busy) return;
    if (!title.trim()) {
      setError(new ApiError('Введите название задачи.', 400));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      // PATCH also requires title. Empty description clears the previous text.
      const input = { title: title.trim(), description };
      if (taskId === null) await tasksApi.create(input);
      else await tasksApi.update(taskId, input);
      onSaved(taskId === null ? 'Задача создана' : 'Изменения сохранены', taskId === null);
    } catch (cause) {
      setError(cause as ApiError);
    } finally {
      setBusy(false);
    }
  }

  async function advance() {
    if (!task || task.status === 'DONE' || busy) return;
    setBusy(true);
    setError(null);
    try {
      await tasksApi.advance(task.id, task.status);
      onSaved(task.status === 'NEW' ? 'Задача взята в работу' : 'Задача завершена', false);
    } catch (cause) {
      setError(cause as ApiError);
    } finally {
      setBusy(false);
    }
  }

  async function deleteTask() {
    if (!task || !confirmingDelete || busy) return;
    setBusy(true);
    setError(null);
    try {
      await tasksApi.delete(task.id);
      onSaved('Задача удалена', false);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 404) {
        // Another client may already have removed it. Refresh the stale list.
        onSaved('Задача уже удалена', false);
      } else setError(cause as ApiError);
    } finally {
      setBusy(false);
    }
  }

  function cancelDelete() {
    setConfirmingDelete(false);
    setError(null);
  }

  return (
    <dialog ref={dialog} className="task-dialog" aria-labelledby="dialog-title" onCancel={(event) => {
      event.preventDefault();
      if (!busy) {
        if (confirmingDelete) cancelDelete();
        else onClose();
      }
    }}>
      <div className="dialog-header">
        <div>
          <span className="eyebrow">{taskId === null ? 'НАЧНИТЕ С МАЛЕНЬКОГО ШАГА' : `ЗАДАЧА #${taskId}`}</span>
          <h2 id="dialog-title">{taskId === null ? 'Новая задача' : confirmingDelete ? 'Удалить задачу?' : editing ? 'Редактирование задачи' : 'Детали задачи'}</h2>
        </div>
        <button className="icon-button" aria-label="Закрыть окно" onClick={onClose} disabled={busy} autoFocus={!editing}><Icon name="close" /></button>
      </div>

      {error && <div className="error-message" role="alert">
        <strong>{error.message}</strong>
        {error.details.length > 0 && <ul>{error.details.map((detail, index) => <li key={index}>{detail}</li>)}</ul>}
        {taskId !== null && !confirmingDelete && (!task || error.status === 409) && <button className="text-button" onClick={() => { setEditing(false); setReload(value => value + 1); }}>Обновить задачу</button>}
      </div>}

      {loading ? <div className="dialog-loading" role="status"><span className="spinner" />Загружаем задачу…</div> : confirmingDelete && task ? (
        <>
          <div className="dialog-body">
            <h3 className="delete-title">{task.title}</h3>
            <p className="delete-description">Задача и её описание будут удалены. Восстановить их не получится.</p>
          </div>
          <div className="dialog-footer">
            <button ref={cancelDeleteButton} className="button secondary" disabled={busy} onClick={cancelDelete}>Отмена</button>
            <button className="button danger" disabled={busy} onClick={deleteTask}><Icon name="trash" size={17} />{busy ? 'Удаляем…' : 'Удалить задачу'}</button>
          </div>
        </>
      ) : editing ? (
        <form onSubmit={save}>
          <div className="dialog-body">
            <label className="field-label" htmlFor="task-title">Название <span className="required">*</span></label>
            <input id="task-title" value={title} onChange={event => setTitle(event.target.value)} placeholder="Что нужно сделать?" maxLength={127} required autoFocus disabled={busy} />
            <div className="field-hint">Коротко и по делу <span>{title.length}/127</span></div>
            <label className="field-label" htmlFor="task-description">Описание <span className="optional">необязательно</span></label>
            <textarea id="task-description" value={description} onChange={event => setDescription(event.target.value)} placeholder="Добавьте детали, чтобы ничего не упустить…" maxLength={2047} rows={6} disabled={busy} />
            <div className="field-hint"><span>Контекст, шаги или ожидаемый результат</span><span>{description.length}/2047</span></div>
            {taskId === null && <div className="form-note"><span className="status-dot" />Новая задача появится в списке «Новые».</div>}
          </div>
          <div className="dialog-footer">
            <button type="button" className="button secondary" disabled={busy} onClick={() => {
              if (task) { setTitle(task.title); setDescription(task.description ?? ''); setEditing(false); setError(null); }
              else onClose();
            }}>Отмена</button>
            <button className="button primary" disabled={busy}>{busy ? 'Сохраняем…' : taskId === null ? 'Создать задачу' : 'Сохранить изменения'}<Icon name="arrow" size={18} /></button>
          </div>
        </form>
      ) : task && (
        <>
          <div className="dialog-body">
            <StatusBadge status={task.status} />
            <h3 className="detail-title">{task.title}</h3>
            <p className={`detail-description ${task.description ? '' : 'muted'}`}>{task.description || 'Описание пока не добавлено.'}</p>
            <div className="detail-date"><Icon name="clock" size={17} />Создана {formatDate(task.createdAt, true)}</div>
          </div>
          <div className="dialog-footer task-actions">
            <button ref={deleteButton} className="button danger-outline delete-trigger" disabled={busy} onClick={() => { setError(null); setConfirmingDelete(true); }}><Icon name="trash" size={17} />Удалить</button>
            <button className="button secondary" disabled={busy} onClick={() => { setEditing(true); setError(null); }}><Icon name="edit" size={17} />Редактировать</button>
            {task.status !== 'DONE' && <button className="button primary" disabled={busy || error?.status === 409} onClick={advance}>{busy ? 'Сохраняем…' : task.status === 'NEW' ? 'Взять в работу' : 'Завершить'}<Icon name={task.status === 'NEW' ? 'arrow' : 'check'} size={18} /></button>}
          </div>
        </>
      )}
    </dialog>
  );
}
