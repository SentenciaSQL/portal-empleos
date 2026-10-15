import { Component, computed, input } from '@angular/core';

export function richTextPlain(value: string | null | undefined): string {
  const raw = (value ?? '').replace(/\u00a0/g, ' ').trim();
  if (!raw) return '';
  if (!raw.startsWith('<')) return raw.replace(/\s+/g, ' ').trim();
  const doc = new DOMParser().parseFromString(raw, 'text/html');
  return (doc.body.textContent ?? '').replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
}

export function toEditorHtml(value: string | null | undefined): string {
  const raw = (value ?? '').trim();
  if (!raw) return '';
  if (raw.startsWith('<')) return raw;
  return raw
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .split(/\n{2,}/)
    .map((block) => `<p>${escapeHtml(block).replace(/\n/g, '<br>')}</p>`)
    .join('');
}

export function isSafeUrl(value: string): boolean {
  const trimmed = value.trim();
  if (!trimmed || /\s/.test(trimmed)) return false;
  try {
    const url = new URL(trimmed);
    if (url.protocol === 'mailto:') return url.pathname.length > 0;
    return (url.protocol === 'http:' || url.protocol === 'https:') && url.hostname.length > 0;
  } catch {
    return false;
  }
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

@Component({
  selector: 'app-rich-text-view',
  template: `<div class="job-html" [innerHTML]="html()"></div>`,
  host: { class: 'block' },
})
export class RichTextView {
  readonly value = input<string | null | undefined>('');
  readonly html = computed(() => toEditorHtml(this.value()));
}
