import { useEffect, useRef, useState } from 'react';
import { tasksApi, type SortMode, type TaskFilter, type TaskPage, type TaskStatus } from './api';
import TaskDialog from './TaskDialog';
import { formatDate, Icon, StatusBadge, statuses } from './ui';

const statusKeys: TaskStatus[] = ['NEW', 'IN_PROGRESS', 'DONE'];
type Counts = Record<TaskStatus, number | null>;
const unknownCounts: Counts = { NEW: null, IN_PROGRESS: null, DONE: null };

export default function App() {
  const [filter, setFilter] = useState<TaskFilter>('');
  const [sortMode, setSortMode] = useState<SortMode>('DESC');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [data, setData] = useState<TaskPage | null>(null);
  const [counts, setCounts] = useState<Counts>(unknownCounts);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [dialog, setDialog] = useState<{ taskId: number | null } | null>(null);
  const [notice, setNotice] = useState('');
  const dialogTrigger = useRef<HTMLElement | null>(null);

  function openDialog(taskId: number | null) {
    dialogTrigger.current = document.activeElement as HTMLElement | null;
    setDialog({ taskId });
  }

  function closeDialog() {
    setDialog(null);
    requestAnimationFrame(() => {
      const target = dialogTrigger.current;
      if (target?.isConnected) target.focus();
      else document.querySelector<HTMLButtonElement>('.create-button')?.focus();
    });
  }

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setData(null);
    tasksApi.list({ status: filter, sortMode, page, size }, controller.signal).then(result => {
      if (controller.signal.aborted) return;
      if (page > 0 && page >= result.totalPages) {
        setPage(Math.max(0, result.totalPages - 1));
      } else setData(result);
    }).catch((cause: Error) => {
      if (!controller.signal.aborted) setError(cause.message);
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [filter, sortMode, page, size, revision]);

  useEffect(() => {
    const controller = new AbortController();
    setCounts(unknownCounts);
    // The API has no statistics endpoint. Fetch only one item per status and use totalElements.
    statusKeys.forEach(status => {
      tasksApi.list({ status, size: 1 }, controller.signal).then(result => {
        if (!controller.signal.aborted) setCounts(current => ({ ...current, [status]: result.totalElements }));
      }).catch(() => { /* Unknown counts stay as a dash; the task list reports its own errors. */ });
    });
    return () => controller.abort();
  }, [revision]);

  useEffect(() => {
    if (!notice) return;
    const timeout = window.setTimeout(() => setNotice(''), 4500);
    return () => window.clearTimeout(timeout);
  }, [notice]);

  function selectFilter(value: TaskFilter) {
    setFilter(value);
    setPage(0);
  }

  const total = statusKeys.every(status => counts[status] !== null)
    ? statusKeys.reduce((sum, status) => sum + counts[status]!, 0) : null;
  const progress = total ? Math.round((counts.DONE! / total) * 100) : 0;
  const heading = filter ? statuses[filter].plural : 'Все задачи';

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="./" aria-label="Simple Tasker — главная"><span className="brand-mark"><Icon name="check" size={24} /></span><span>simple<span className="brand-light">tasker</span><span className="brand-dot">.</span></span></a>
        <div className="workspace-label">ЛИЧНОЕ ПРОСТРАНСТВО</div>
        <nav aria-label="Разделы задач" className="main-nav">
          <button className={`nav-item ${filter === '' ? 'active' : ''}`} onClick={() => selectFilter('')} aria-current={filter === '' ? 'page' : undefined}><Icon name="list" /><span>Все задачи</span><span className="nav-count">{total ?? '—'}</span></button>
          <div className="nav-separator" />
          {statusKeys.map(status => <button key={status} className={`nav-item ${filter === status ? 'active' : ''}`} onClick={() => selectFilter(status)} aria-current={filter === status ? 'page' : undefined}><Icon name={statuses[status].icon} /><span>{statuses[status].plural}</span><span className="nav-count">{counts[status] ?? '—'}</span></button>)}
        </nav>
        <div className="sidebar-note"><span className="note-decoration"><Icon name="check" size={20} /></span><strong>Шаг за шагом.</strong><p>Большие планы начинаются<br />с одной небольшой задачи.</p></div>
        <div className="sidebar-footer"><span className="small-logo">st.</span><div>Simple Tasker<small>Меньше шума. Больше дела.</small></div></div>
      </aside>

      <div className="main-shell">
        <header className="topbar"><span>Рабочее пространство <span className="breadcrumb-separator">/</span> <strong>Мои задачи</strong></span><span className="today">{new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date())}</span><span className="mobile-brand">simpletasker.</span></header>
        <main>
          <section className="page-heading">
            <div><div className="eyebrow">ПЛАНИРУЙТЕ. ДЕЛАЙТЕ. ЗАВЕРШАЙТЕ.</div><h1>Мои задачи<span className="heading-dot">.</span></h1><p>Освободите голову. Всё важное — здесь.</p></div>
            <button className="button primary create-button" onClick={() => openDialog(null)}><Icon name="plus" size={19} />Новая задача</button>
          </section>

          <section className="stats-grid" aria-label="Обзор задач">
            {statusKeys.map((status, index) => <button key={status} className={`stat-card stat-${status} ${filter === status ? 'selected' : ''}`} onClick={() => selectFilter(status)} aria-pressed={filter === status}>
              <div className="stat-top"><span>{statuses[status].plural}</span><span className={`stat-icon stat-icon-${status}`}><Icon name={statuses[status].icon} size={19} /></span></div>
              <div className="stat-number">{counts[status] ?? '—'}<span>{['Ждут своего часа', 'В фокусе сейчас', 'Уже позади'][index]}</span></div>
            </button>)}
          </section>

          <section className="tasks-panel" aria-label="Список задач">
            <div className="panel-heading"><div><h2>{heading}</h2>{data && <span className="total-badge">{data.totalElements}</span>}</div><button className="icon-button refresh-button" aria-label="Обновить список" title="Обновить список" disabled={loading} onClick={() => setRevision(value => value + 1)}><Icon name="refresh" size={18} /></button></div>
            <div className="toolbar">
              <div className="filter-tabs" aria-label="Фильтр по статусу">{(['', ...statusKeys] as TaskFilter[]).map(value => <button key={value} aria-pressed={filter === value} className={`filter-tab ${filter === value ? 'selected' : ''}`} onClick={() => selectFilter(value)}>{value ? statuses[value].plural : 'Все'}</button>)}</div>
              <label className="sort-label"><span className="sr-only">Порядок сортировки</span><select value={sortMode} onChange={event => { setSortMode(event.target.value as SortMode); setPage(0); }}><option value="DESC">Сначала новые</option><option value="ASC">Сначала старые</option></select></label>
            </div>

            <div className="task-list" aria-busy={loading}>
              {loading ? <div className="list-state" role="status"><span className="spinner" /><h3>Загружаем задачи…</h3><p>Собираем всё важное в одном месте.</p></div> : error ? <div className="list-state" role="alert"><span className="state-icon error-icon"><Icon name="inbox" size={28} /></span><h3>Не удалось загрузить задачи</h3><p>{error}</p><button className="button secondary" onClick={() => setRevision(value => value + 1)}><Icon name="refresh" size={16} />Повторить попытку</button></div> : data?.items.length ? (
                <>
                  <div className="list-columns" aria-hidden="true"><span>ЗАДАЧА</span><span>СТАТУС</span><span>СОЗДАНА</span><span /></div>
                  {data.items.map(task => <button key={task.id} className="task-row" onClick={() => openDialog(task.id)} aria-label={`Открыть задачу: ${task.title}`}>
                    <span className="task-name"><span className={`task-state-icon task-state-${task.status}`}><Icon name={statuses[task.status].icon} size={18} /></span><span><strong>{task.title}</strong><small>#{task.id}</small></span></span>
                    <StatusBadge status={task.status} />
                    <time dateTime={task.createdAt}>{formatDate(task.createdAt)}</time>
                    <span className="row-arrow"><Icon name="right" size={17} /></span>
                  </button>)}
                </>
              ) : <div className="list-state"><span className="state-icon"><Icon name="inbox" size={31} /></span><h3>{filter ? 'Здесь пока нет задач' : 'Место для ваших планов'}</h3><p>{filter === 'DONE' ? 'Завершайте задачи — здесь будут ваши результаты.' : filter === 'IN_PROGRESS' ? 'Возьмите новую задачу в работу, чтобы начать.' : 'Добавьте первую задачу и сделайте шаг к результату.'}</p><button className="button secondary" onClick={() => filter && filter !== 'NEW' ? selectFilter('NEW') : openDialog(null)}>{filter && filter !== 'NEW' ? 'Перейти к новым' : 'Создать задачу'}<Icon name="arrow" size={17} /></button></div>}
            </div>

            <footer className="pagination">
              <span className="range-text">{data && data.totalElements > 0 ? `${data.page * data.size + 1}–${data.page * data.size + data.items.length} из ${data.totalElements}` : '0 задач'}</span>
              <label className="page-size">На странице <select aria-label="Задач на странице" value={size} onChange={event => { setSize(Number(event.target.value)); setPage(0); }}>{[10, 20, 50, 100].map(value => <option key={value}>{value}</option>)}</select></label>
              <div className="page-controls"><button className="icon-button" aria-label="Предыдущая страница" disabled={loading || !!error || page === 0} onClick={() => setPage(value => value - 1)}><Icon name="left" size={17} /></button><span>{data ? `${data.page + 1} / ${Math.max(1, data.totalPages)}` : '—'}</span><button className="icon-button" aria-label="Следующая страница" disabled={loading || !data || page + 1 >= data.totalPages} onClick={() => setPage(value => value + 1)}><Icon name="right" size={17} /></button></div>
            </footer>
          </section>

          <div className="bottom-note"><Icon name="check" size={16} /><span>Каждая завершённая задача — маленькая победа.</span>{total !== null && total > 0 && <span className="progress-caption">Выполнено {progress}%<span className="progress-track"><span style={{ width: `${progress}%` }} /></span></span>}</div>
        </main>
      </div>
      {dialog && <TaskDialog taskId={dialog.taskId} onClose={closeDialog} onSaved={(message, created) => {
        closeDialog();
        setNotice(message);
        if (created) { setFilter(''); setPage(0); setSortMode('DESC'); }
        setRevision(value => value + 1);
      }} />}
      {notice && <div className="toast" role="status"><span><Icon name="check" size={18} /></span>{notice}<button className="icon-button" aria-label="Скрыть уведомление" onClick={() => setNotice('')}><Icon name="close" size={16} /></button></div>}
    </div>
  );
}
