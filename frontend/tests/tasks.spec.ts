import { test, expect, type Page } from '@playwright/test';
import type { Task } from '../src/api';

const initialTasks: Task[] = Array.from({ length: 12 }, (_, index) => ({
  id: index + 1,
  title: `Задача ${index + 1}`,
  description: `Подробное описание ${index + 1}`,
  status: index === 0 ? 'IN_PROGRESS' : index === 1 ? 'DONE' : 'NEW',
  createdAt: new Date(Date.UTC(2026, 8, index + 1, 9)).toISOString(),
}));

async function mockApi(page: Page, seed = initialTasks) {
  const tasks = structuredClone(seed);
  const writes: { method: string; path: string; body: unknown }[] = [];
  await page.route('**/api/v1/tasks**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const parts = url.pathname.split('/').filter(Boolean);
    const id = Number(parts[3]);
    const task = tasks.find(item => item.id === id);
    const method = request.method();
    if (method !== 'GET') writes.push({ method, path: url.pathname, body: request.postDataJSON() });
    if (method === 'GET' && !id) {
      const status = url.searchParams.get('status');
      const filtered = tasks.filter(item => !status || item.status === status)
        .sort((a, b) => (a.createdAt.localeCompare(b.createdAt)) * (url.searchParams.get('sortMode') === 'ASC' ? 1 : -1));
      const pageNumber = Number(url.searchParams.get('page'));
      const size = Number(url.searchParams.get('size'));
      return route.fulfill({ json: {
        items: filtered.slice(pageNumber * size, (pageNumber + 1) * size).map(({ description: _, ...summary }) => summary),
        page: pageNumber, size, totalElements: filtered.length, totalPages: Math.ceil(filtered.length / size),
      } });
    }
    if (method === 'POST' && !id) {
      const created: Task = { ...request.postDataJSON(), id: 100, status: 'NEW', createdAt: '2026-10-01T12:00:00Z' };
      tasks.push(created);
      return route.fulfill({ status: 201, json: created });
    }
    if (!task) return route.fulfill({ status: 404, json: { message: 'Task not found' } });
    if (method === 'DELETE') {
      tasks.splice(tasks.indexOf(task), 1);
      return route.fulfill({ status: 204 });
    }
    if (method === 'PATCH') Object.assign(task, request.postDataJSON());
    if (method === 'POST') {
      if (parts[4] === 'start' && task.status === 'NEW') task.status = 'IN_PROGRESS';
      else if (parts[4] === 'complete' && task.status === 'IN_PROGRESS') task.status = 'DONE';
      else return route.fulfill({ status: 409, json: { message: 'Conditions not met' } });
    }
    return route.fulfill({ json: task });
  });
  return { tasks, writes };
}

test('loads paginated summaries and fetches the full task when opened', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  const rows = page.getByRole('button', { name: /^Открыть задачу:/ });
  await expect(rows).toHaveCount(10);
  await expect(rows.first()).toHaveAccessibleName('Открыть задачу: Задача 12');
  await page.getByRole('button', { name: 'Следующая страница' }).click();
  await expect(rows).toHaveCount(2);
  await page.getByRole('button', { name: 'Открыть задачу: Задача 1', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Подробное описание 1');
  await expect(page.getByRole('button', { name: 'Завершить', exact: true })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Открыть задачу: Задача 1', exact: true })).toBeFocused();
});

test('filters on the server, resets pagination and supports ascending order and page size', async ({ page }) => {
  await mockApi(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Следующая страница' }).click();
  const filterRequest = page.waitForRequest(request => {
    const url = new URL(request.url());
    return url.pathname === '/api/v1/tasks' && url.searchParams.get('status') === 'DONE' && url.searchParams.get('size') === '10' && url.searchParams.get('page') === '0';
  });
  await page.getByRole('button', { name: 'Завершённые', exact: true }).click();
  await filterRequest;
  await expect(page.getByRole('button', { name: /^Открыть задачу:/ })).toHaveCount(1);
  await expect(page.getByRole('button', { name: 'Предыдущая страница' })).toBeDisabled();
  await page.getByRole('button', { name: 'Все', exact: true }).click();
  await page.getByLabel('Порядок сортировки').selectOption('ASC');
  await expect(page.getByRole('button', { name: /^Открыть задачу:/ }).first()).toHaveAccessibleName('Открыть задачу: Задача 1');
  await page.getByLabel('Задач на странице').selectOption('20');
  await expect(page.getByRole('button', { name: /^Открыть задачу:/ })).toHaveCount(12);
});

test('validates whitespace title and creates a task with trimmed title', async ({ page }) => {
  const { writes } = await mockApi(page, []);
  await page.goto('/');
  await expect(page.getByText('Место для ваших планов')).toBeVisible();
  await page.getByRole('button', { name: 'Новая задача', exact: true }).click();
  await page.getByLabel('Название').fill('   ');
  await page.getByRole('button', { name: 'Создать задачу', exact: true }).last().click();
  await expect(page.getByRole('alert')).toContainText('Введите название');
  expect(writes).toHaveLength(0);
  await page.getByLabel('Название').fill('  Изучить HTTP  ');
  await page.getByLabel('Описание').fill('Запрос и ответ\nКоды состояния');
  await page.getByRole('dialog').getByRole('button', { name: 'Создать задачу' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Открыть задачу: Изучить HTTP' })).toBeVisible();
  expect(writes).toEqual([{ method: 'POST', path: '/api/v1/tasks', body: { title: 'Изучить HTTP', description: 'Запрос и ответ\nКоды состояния' } }]);
});

test('edits a full task and clears description with an empty string', async ({ page }) => {
  const { writes } = await mockApi(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Открыть задачу: Задача 12', exact: true }).click();
  await page.getByRole('button', { name: 'Редактировать', exact: true }).click();
  await expect(page.getByLabel('Описание')).toHaveValue('Подробное описание 12');
  await page.getByLabel('Описание').fill('');
  await page.getByRole('button', { name: 'Сохранить изменения' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(writes).toEqual([{ method: 'PATCH', path: '/api/v1/tasks/12', body: { title: 'Задача 12', description: '' } }]);
  await page.getByRole('button', { name: 'Открыть задачу: Задача 12', exact: true }).click();
  await expect(page.getByText('Описание пока не добавлено.')).toBeVisible();
});

test('allows only NEW → IN_PROGRESS → DONE transitions', async ({ page }) => {
  const { writes } = await mockApi(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Открыть задачу: Задача 12', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Завершить', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Взять в работу' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Открыть задачу: Задача 12', exact: true }).click();
  await page.getByRole('button', { name: 'Завершить', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: 'Открыть задачу: Задача 12', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Готово');
  await expect(page.getByRole('button', { name: 'Взять в работу' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Завершить', exact: true })).toHaveCount(0);
  expect(writes.map(write => write.path)).toEqual(['/api/v1/tasks/12/start', '/api/v1/tasks/12/complete']);
});

test('recovers from a failed list request without presenting it as an empty list', async ({ page }) => {
  await page.route('**/api/v1/tasks**', route => route.fulfill({ status: 502, contentType: 'text/html', body: 'Bad gateway' }));
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('Не удалось загрузить задачи');
  await expect(page.getByText('Место для ваших планов')).toHaveCount(0);
  await mockApi(page);
  await page.getByRole('button', { name: 'Повторить попытку' }).click();
  await expect(page.getByRole('button', { name: /^Открыть задачу:/ })).toHaveCount(10);
});

test('preserves input after a failed save and blocks duplicate submissions', async ({ page }) => {
  await mockApi(page);
  let submissions = 0;
  await page.route('**/api/v1/tasks', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    submissions++;
    await new Promise(resolve => setTimeout(resolve, 300));
    await route.fulfill({ status: 400, json: { errors: ['title: rejected by server'] } });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Новая задача', exact: true }).click();
  await page.getByLabel('Название').fill('Мой черновик');
  await page.getByRole('dialog').getByRole('button', { name: 'Создать задачу' }).click();
  await expect(page.getByRole('button', { name: 'Сохраняем…' })).toBeDisabled();
  await expect(page.getByRole('alert')).toContainText('title: rejected by server');
  await expect(page.getByLabel('Название')).toHaveValue('Мой черновик');
  expect(submissions).toBe(1);
});

test('reloads a task after a state conflict', async ({ page }) => {
  const { tasks } = await mockApi(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Открыть задачу: Задача 12', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Взять в работу' })).toBeVisible();
  tasks.find(task => task.id === 12)!.status = 'DONE';
  await page.getByRole('button', { name: 'Взять в работу' }).click();
  await expect(page.getByRole('alert')).toContainText('Состояние задачи изменилось');
  await expect(page.getByRole('button', { name: 'Взять в работу' })).toBeDisabled();
  await page.getByRole('button', { name: 'Обновить задачу' }).click();
  await expect(page.getByRole('dialog')).toContainText('Готово');
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('keeps the selected filter when an older response arrives late', async ({ page }) => {
  await mockApi(page);
  await page.route('**/api/v1/tasks?**', async route => {
    const url = new URL(route.request().url());
    if (url.searchParams.get('status') === 'NEW' && url.searchParams.get('size') === '10') {
      await new Promise(resolve => setTimeout(resolve, 400));
    }
    await route.fallback();
  });
  await page.goto('/');
  await expect(page.getByRole('button', { name: /^Открыть задачу:/ })).toHaveCount(10);
  await page.getByRole('button', { name: 'Новые', exact: true }).click();
  await page.getByRole('button', { name: 'Завершённые', exact: true }).click();
  await expect(page.getByRole('button', { name: /^Открыть задачу:/ })).toHaveCount(1);
  await expect(page.getByRole('button', { name: 'Открыть задачу: Задача 2', exact: true })).toBeVisible();
  // Let the deliberately delayed old response finish before checking the final screen.
  await page.waitForTimeout(500);
  await expect(page.getByRole('button', { name: /^Открыть задачу:/ })).toHaveCount(1);
});

test('fits the viewport and renders user content as text', async ({ page }) => {
  await mockApi(page, [{ ...initialTasks[0], title: '<img src=x onerror=alert(1)>', description: '<script>alert(1)</script>' }]);
  await page.goto('/');
  await page.getByRole('button', { name: /^Открыть задачу:/ }).click();
  await expect(page.getByRole('dialog')).toContainText('<script>alert(1)</script>');
  await expect(page.getByRole('dialog').locator('img, script')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  const bounds = await page.getByRole('dialog').boundingBox();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
});

test('requires confirmation and cancels deletion without sending a request', async ({ page }) => {
  const { writes } = await mockApi(page);
  await page.goto('/');
  await page.getByRole('button', { name: 'Открыть задачу: Задача 12', exact: true }).click();
  await page.getByRole('button', { name: 'Удалить', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Удалить задачу?' });
  await expect(dialog).toContainText('Задача 12');
  await expect(dialog).toContainText('Восстановить их не получится.');
  await expect(page.getByRole('button', { name: 'Отмена', exact: true })).toBeFocused();
  await page.getByRole('button', { name: 'Отмена', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Детали задачи' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Удалить', exact: true })).toBeFocused();
  await page.getByRole('button', { name: 'Удалить', exact: true }).click();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'Детали задачи' })).toBeVisible();
  expect(writes).toHaveLength(0);
});

for (const status of ['NEW', 'IN_PROGRESS', 'DONE'] as const) {
  test(`deletes a ${status} task with 204 and refreshes the list and counters`, async ({ page }) => {
    const { writes } = await mockApi(page, [{ ...initialTasks[0], status }]);
    await page.goto('/');
    await expect(page.locator(`.stat-${status} .stat-number`)).toContainText('1');
    await page.getByRole('button', { name: 'Открыть задачу: Задача 1', exact: true }).click();
    await page.getByRole('button', { name: 'Удалить', exact: true }).click();
    await page.getByRole('button', { name: 'Удалить задачу', exact: true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText('Задача удалена');
    await expect(page.getByText('Место для ваших планов')).toBeVisible();
    await expect(page.locator(`.stat-${status} .stat-number`)).toContainText('0');
    await expect(page.getByRole('button', { name: 'Новая задача', exact: true })).toBeFocused();
    expect(writes).toEqual([{ method: 'DELETE', path: '/api/v1/tasks/1', body: null }]);
  });
}

test('returns to the previous page after deleting its last task and retains sorting and filter', async ({ page }) => {
  await mockApi(page, initialTasks.slice(0, 11).map(task => ({ ...task, status: 'NEW' })));
  await page.goto('/');
  await page.getByRole('button', { name: 'Новые', exact: true }).click();
  await page.getByLabel('Порядок сортировки').selectOption('ASC');
  await page.getByRole('button', { name: 'Следующая страница' }).click();
  await expect(page.getByRole('button', { name: /^Открыть задачу:/ })).toHaveCount(1);
  await page.getByRole('button', { name: 'Открыть задачу: Задача 11', exact: true }).click();
  await page.getByRole('button', { name: 'Удалить', exact: true }).click();
  await page.getByRole('button', { name: 'Удалить задачу', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^Открыть задачу:/ })).toHaveCount(10);
  await expect(page.getByRole('button', { name: /^Открыть задачу:/ }).first()).toHaveAccessibleName('Открыть задачу: Задача 1');
  await expect(page.getByRole('button', { name: 'Новые', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByLabel('Порядок сортировки')).toHaveValue('ASC');
  await expect(page.getByRole('button', { name: 'Предыдущая страница' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Следующая страница' })).toBeDisabled();
  await expect(page.locator('.range-text')).toHaveText('1–10 из 10');
});

test('keeps a task after failed deletion and allows retry while preventing duplicate requests', async ({ page }) => {
  const { tasks, writes } = await mockApi(page, [initialTasks[0]]);
  let releaseRequest!: () => void;
  const pendingRequest = new Promise<void>(resolve => { releaseRequest = resolve; });
  let attempts = 0;
  await page.route('**/api/v1/tasks/1', async route => {
    if (route.request().method() !== 'DELETE') return route.fallback();
    attempts++;
    if (attempts > 1) return route.fallback();
    await pendingRequest;
    await route.fulfill({ status: 500, json: { message: 'Something went wrong' } });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Открыть задачу: Задача 1', exact: true }).click();
  await page.getByRole('button', { name: 'Удалить', exact: true }).click();
  await page.getByRole('button', { name: 'Удалить задачу', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Удаляем…', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Отмена', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Закрыть окно', exact: true })).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog', { name: 'Удалить задачу?' })).toBeVisible();
  releaseRequest();
  await expect(page.getByRole('alert')).toContainText('Сервер не смог обработать запрос');
  expect(tasks).toHaveLength(1);
  expect(attempts).toBe(1);
  await page.getByRole('button', { name: 'Удалить задачу', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByText('Место для ваших планов')).toBeVisible();
  expect(attempts).toBe(2);
  expect(writes).toHaveLength(1);
});

test('refreshes stale data when the task has already been deleted by another client', async ({ page }) => {
  const { tasks, writes } = await mockApi(page, [initialTasks[0]]);
  await page.goto('/');
  await page.getByRole('button', { name: 'Открыть задачу: Задача 1', exact: true }).click();
  await page.getByRole('button', { name: 'Удалить', exact: true }).click();
  tasks.splice(0);
  await page.getByRole('button', { name: 'Удалить задачу', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('status')).toContainText('Задача уже удалена');
  await expect(page.getByText('Место для ваших планов')).toBeVisible();
  await expect(page.locator('.stat-IN_PROGRESS .stat-number')).toContainText('0');
  expect(writes).toEqual([{ method: 'DELETE', path: '/api/v1/tasks/1', body: null }]);
});
