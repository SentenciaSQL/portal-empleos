import { Component, OnDestroy, booleanAttribute, inject, input, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NgControl } from '@angular/forms';
import { I18nService } from '../core/i18n.service';
import { Envelope, Named } from '../core/models';

let nextSkillPickerId = 0;

function collapse(raw: string): string {
  return raw.replace(/\u0000/g, ' ').trim().replace(/\s+/g, ' ');
}

function keyOf(name: string): string {
  return collapse(name).toLowerCase();
}

@Component({
  selector: 'app-skill-picker',
  templateUrl: './skill-picker.html',
})
export class SkillPicker implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly ngControl = inject(NgControl, { optional: true, self: true });
  private onChange: (value: number[]) => void = () => undefined;
  private onTouched: () => void = () => undefined;
  private catalog: Named[] = [];
  private catalogReady = false;
  private pending: number[] | null = null;
  private suggestTimer: ReturnType<typeof setTimeout> | null = null;
  private suggestSeq = 0;
  private suggestionQuery = '';

  readonly i18n = inject(I18nService);
  readonly allowCreate = input(false, { transform: booleanAttribute });
  readonly labelledBy = input('');
  readonly selected = signal<Named[]>([]);
  readonly suggestions = signal<Named[]>([]);
  readonly open = signal(false);
  readonly active = signal(0);
  readonly query = signal('');
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly listId = `skill-list-${++nextSkillPickerId}`;
  readonly inputId = `skill-input-${nextSkillPickerId}`;
  readonly hintId = `skill-hint-${nextSkillPickerId}`;

  constructor() {
    if (this.ngControl) this.ngControl.valueAccessor = this;
    this.http.get<Envelope<Named[]>>('/api/catalog/skills').subscribe((res) => {
      this.catalog = res.data;
      this.catalogReady = true;
      this.applyIds(this.pending ?? [], true);
    });
  }

  ngOnDestroy(): void {
    if (this.suggestTimer) clearTimeout(this.suggestTimer);
  }

  writeValue(value: number[] | null): void {
    const ids = value ?? [];
    this.pending = ids;
    if (this.catalogReady) this.applyIds(ids, false);
  }

  registerOnChange(fn: (value: number[]) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  onInput(value: string): void {
    this.query.set(value);
    this.error.set(null);
    this.active.set(0);
    if (this.suggestTimer) clearTimeout(this.suggestTimer);
    const display = collapse(value);
    if (!display) {
      this.suggestions.set([]);
      this.open.set(false);
      this.suggestionQuery = '';
      return;
    }
    this.suggestTimer = setTimeout(() => {
      this.suggestTimer = null;
      this.fetchSuggestions(display);
    }, 200);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'ArrowDown' && this.open()) {
      event.preventDefault();
      const last = this.suggestions().length - 1;
      this.active.update((index) => Math.min(index + 1, last));
      return;
    }
    if (event.key === 'ArrowUp' && this.open()) {
      event.preventDefault();
      this.active.update((index) => Math.max(index - 1, 0));
      return;
    }
    if (event.key === 'Escape') {
      this.open.set(false);
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      this.commit();
    }
  }

  touch(): void {
    this.onTouched();
  }

  commit(): void {
    this.onTouched();
    if (this.busy()) return;
    const display = collapse(this.query());
    if (this.suggestTimer && display) {
      clearTimeout(this.suggestTimer);
      this.suggestTimer = null;
      this.fetchSuggestions(display, () => this.finishCommit());
      return;
    }
    this.finishCommit();
  }

  choose(skill: Named): void {
    this.add(skill);
  }

  remove(id: number): void {
    const next = this.selected().filter((item) => item.id !== id);
    this.selected.set(next);
    this.onChange(next.map((item) => item.id));
    this.onTouched();
  }

  optionId(skill: Named): string {
    return `${this.listId}-${skill.id}`;
  }

  activeDescendant(): string | null {
    const item = this.suggestions()[this.active()];
    return this.open() && item ? this.optionId(item) : null;
  }

  private finishCommit(): void {
    const display = collapse(this.query());
    const key = keyOf(display);
    if (!display || !/[\p{L}\p{N}]/u.test(display)) {
      this.error.set('Escribe el nombre de la habilidad.');
      this.open.set(false);
      return;
    }
    if (/[<>]/.test(display) || /[\u0000-\u001F\u007F-\u009F]/.test(display)) {
      this.error.set('El nombre de la habilidad contiene caracteres no permitidos.');
      return;
    }
    if (display.length > 80) {
      this.error.set('El nombre de la habilidad no puede superar 80 caracteres.');
      return;
    }
    if (this.selected().some((item) => keyOf(item.name) === key)) {
      this.error.set('Esa habilidad ya está en la oferta.');
      this.clearQuery();
      return;
    }
    const exact = [...this.suggestions(), ...this.catalog].find((item) => keyOf(item.name) === key);
    if (exact) {
      this.add(exact);
      return;
    }
    const highlighted = this.suggestions()[this.active()] ?? this.suggestions()[0];
    if (this.open() && highlighted && keyOf(highlighted.name).startsWith(key)) {
      this.add(highlighted);
      return;
    }
    if (this.suggestionQuery !== key && display) {
      this.fetchSuggestions(display, () => this.finishCommit());
      return;
    }
    if (!this.allowCreate()) {
      this.error.set('Esa habilidad no está en el catálogo.');
      return;
    }
    if (display.length < 2) {
      this.error.set('La habilidad debe tener al menos 2 caracteres.');
      return;
    }
    if (this.selected().length >= 30) {
      this.error.set('Puedes asociar hasta 30 habilidades.');
      return;
    }
    this.busy.set(true);
    this.http.post<Envelope<Named>>('/api/company/skills', { name: display }).subscribe({
      next: (res) => {
        this.busy.set(false);
        this.remember(res.data);
        this.add(res.data);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(this.i18n.apiError(err));
      },
    });
  }

  private fetchSuggestions(display: string, after?: () => void): void {
    const key = keyOf(display);
    const seq = ++this.suggestSeq;
    this.http.get<Envelope<Named[]>>('/api/catalog/skills', { params: { q: display } }).subscribe({
      next: (res) => {
        if (seq !== this.suggestSeq || keyOf(this.query()) !== key) return;
        const chosen = new Set(this.selected().map((item) => item.id));
        const next = res.data.filter((item) => !chosen.has(item.id));
        this.suggestionQuery = key;
        this.suggestions.set(next);
        this.open.set(next.length > 0);
        this.active.set(0);
        after?.();
      },
      error: () => {
        if (seq !== this.suggestSeq || keyOf(this.query()) !== key) return;
        this.suggestionQuery = key;
        this.open.set(false);
        after?.();
      },
    });
  }

  private add(skill: Named): void {
    if (this.selected().some((item) => item.id === skill.id || keyOf(item.name) === keyOf(skill.name))) {
      this.error.set('Esa habilidad ya está en la oferta.');
      this.clearQuery();
      return;
    }
    if (this.selected().length >= 30) {
      this.error.set('Puedes asociar hasta 30 habilidades.');
      return;
    }
    this.remember(skill);
    const next = [...this.selected(), skill];
    this.selected.set(next);
    this.onChange(next.map((item) => item.id));
    this.clearQuery();
    this.error.set(null);
  }

  private remember(skill: Named): void {
    if (!this.catalog.some((item) => item.id === skill.id)) this.catalog = [...this.catalog, skill];
  }

  private applyIds(ids: number[], keepUnsaved: boolean): void {
    const byId = new Map(this.catalog.map((item) => [item.id, item]));
    const resolved = ids.map((id) => byId.get(id)).filter((item): item is Named => !!item);
    const extra = keepUnsaved ? this.selected().filter((item) => !resolved.some((known) => known.id === item.id)) : [];
    this.selected.set([...resolved, ...extra]);
    this.pending = null;
  }

  private clearQuery(): void {
    this.query.set('');
    this.suggestions.set([]);
    this.open.set(false);
    this.suggestionQuery = '';
    this.suggestSeq++;
    if (this.suggestTimer) {
      clearTimeout(this.suggestTimer);
      this.suggestTimer = null;
    }
  }
}
