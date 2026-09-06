import type { TaskStatus } from './api';

const paths = {
  check: 'm5 12 4 4L19 6',
  plus: 'M12 5v14M5 12h14',
  list: 'M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01',
  circle: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  clock: 'M12 8v4l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  arrow: 'M5 12h14m-5-5 5 5-5 5',
  left: 'm14 6-6 6 6 6',
  right: 'm10 6 6 6-6 6',
  close: 'm6 6 12 12M6 18 18 6',
  refresh: 'M20 7v5h-5M4 17v-5h5M6 7a7 7 0 0 1 12-1l2 3M4 15l2 3a7 7 0 0 0 12-1',
  edit: 'm15 5 4 4M4 20l5-1L20 8a2.8 2.8 0 0 0-4-4L5 15l-1 5',
  inbox: 'M4 4h16l2 11v5H2v-5L4 4Zm-2 11h6l2 3h4l2-3h6',
};

export function Icon({ name, size = 20 }: { name: keyof typeof paths; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>;
}

export const statuses = {
  NEW: { label: 'Новая', plural: 'Новые', icon: 'circle' },
  IN_PROGRESS: { label: 'В работе', plural: 'В работе', icon: 'clock' },
  DONE: { label: 'Готово', plural: 'Завершённые', icon: 'check' },
} as const;

export function StatusBadge({ status }: { status: TaskStatus }) {
  return <span className={`status status-${status}`}><span className="status-dot" />{statuses[status].label}</span>;
}

export function formatDate(value: string, full = false) {
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: full ? 'long' : 'short', year: 'numeric',
    ...(full ? { hour: '2-digit', minute: '2-digit' } as const : {}),
  }).format(new Date(value));
}
