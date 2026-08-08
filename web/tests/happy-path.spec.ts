import {expect, test} from '@playwright/test';

test('inspects a trace, runs a scenario, and opens its report', async ({page}) => {
  await page.goto('/');
  await expect(page.getByRole('heading', {name: 'Event traces'})).toBeVisible();
  await expect(page.getByText('Loading captured traces…')).toBeVisible();
  await page.getByRole('button', {name: 'Run scenario'}).click();
  await expect(page.getByRole('heading', {name: 'Scenario runner'})).toBeVisible();
  await page.getByRole('button', {name: 'Start deterministic replay'}).click();
  await expect(page.getByRole('status')).toContainText('Replay running');
  await expect(page.getByRole('heading', {name: 'Replay detail'})).toBeVisible();
  await expect(page.getByText('8bf2a317-c46a-45bc-a2f7-49d4b74c9041')).toBeVisible();
  await page.getByRole('button', {name: /Open divergence report/}).click();
  await expect(page.getByRole('heading', {name: 'Divergence report'})).toBeVisible();
  await expect(page.getByText('First divergence at event 3')).toBeVisible();
  await expect(page.getByRole('table')).toContainText('BLOCKED');
});
