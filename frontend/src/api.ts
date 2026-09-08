export type TaskStatus = 'NEW' | 'IN_PROGRESS' | 'DONE';
export type TaskFilter = TaskStatus | '';
export type SortMode = 'ASC' | 'DESC';

export interface TaskSummary {
  id: number;
  title: string;
  status: TaskStatus;
  createdAt: string;
}

export interface Task extends TaskSummary { description: string | null }
export interface TaskInput { title: string; description: string }
export interface TaskPage {
  items: TaskSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export class ApiError extends Error {
  constructor(message: string, public status: number, public details: string[] = []) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api/v1/tasks${path}`, {
      ...init,
      headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}) },
      cache: 'no-store',
      signal: init.signal
        ? AbortSignal.any([init.signal, AbortSignal.timeout(20_000)])
        : AbortSignal.timeout(20_000),
    });
  } catch (error) {
    if (init.signal?.aborted) throw error;
    throw new ApiError('Не удалось связаться с сервером. Проверьте подключение и повторите попытку.', 0);
  }

  // DELETE succeeds with an empty body; the other endpoints still require JSON.
  if (response.status === 204 && init.method === 'DELETE') return undefined as T;

  const data = await response.json().catch(() => null);
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: 'Проверьте заполненные поля.',
      404: 'Задача не найдена. Возможно, она больше недоступна.',
      409: 'Состояние задачи изменилось. Обновите данные и повторите действие.',
    };
    throw new ApiError(
      messages[response.status] || 'Сервер не смог обработать запрос. Попробуйте ещё раз.',
      response.status,
      Array.isArray(data?.errors) ? data.errors.filter((value: unknown) => typeof value === 'string') : [],
    );
  }
  if (!data) throw new ApiError('Сервер вернул некорректный ответ.', response.status);
  return data as T;
}

export const tasksApi = {
  list({ status = '', sortMode = 'DESC', page = 0, size = 10 }: {
    status?: TaskFilter; sortMode?: SortMode; page?: number; size?: number;
  } = {}, signal?: AbortSignal) {
    const query = new URLSearchParams({ sortBy: 'CREATED_AT', sortMode, page: String(page), size: String(size) });
    if (status) query.set('status', status);
    return request<TaskPage>(`?${query}`, { signal });
  },
  get(id: number, signal?: AbortSignal) {
    return request<Task>(`/${id}`, { signal });
  },
  create(input: TaskInput) {
    return request<Task>('', { method: 'POST', body: JSON.stringify(input) });
  },
  update(id: number, input: TaskInput) {
    return request<Task>(`/${id}`, { method: 'PATCH', body: JSON.stringify(input) });
  },
  delete(id: number) {
    return request<void>(`/${id}`, { method: 'DELETE' });
  },
  advance(id: number, status: 'NEW' | 'IN_PROGRESS') {
    return request<Task>(`/${id}/${status === 'NEW' ? 'start' : 'complete'}`, { method: 'POST' });
  },
};
