import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, OnDestroy, booleanAttribute, effect, inject, input, signal, viewChild } from '@angular/core';
import { AbstractControl, NgControl, ValidationErrors } from '@angular/forms';
import { Editor } from '@tiptap/core';
import { Placeholder } from '@tiptap/extensions';
import { StarterKit } from '@tiptap/starter-kit';
import { I18nService } from '../core/i18n.service';
import { isSafeUrl, richTextPlain, toEditorHtml } from './rich-text';

let nextRichTextId = 0;

export function richTextRequired(control: AbstractControl): ValidationErrors | null {
  return richTextPlain(control.value).length ? null : { required: true };
}

type EditorCommand = 'bold' | 'italic' | 'underline' | 'bulletList' | 'orderedList' | 'undo' | 'redo' | 'clear';

@Component({
  selector: 'app-rich-text',
  templateUrl: './rich-text-editor.html',
})
export class RichTextEditor implements AfterViewInit, OnDestroy {
  private readonly surface = viewChild.required<ElementRef<HTMLElement>>('surface');
  private readonly ngControl = inject(NgControl, { optional: true, self: true });
  private readonly cdr = inject(ChangeDetectorRef);
  private editor: Editor | null = null;
  private pending = '';
  private lastHtml = '';
  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  readonly i18n = inject(I18nService);
  readonly placeholder = input('');
  readonly hint = input('');
  readonly labelledBy = input('');
  readonly requiredMessage = input('');
  readonly tall = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly disabled = signal(false);
  readonly linkOpen = signal(false);
  readonly linkDraft = signal('https://');
  readonly linkError = signal<string | null>(null);
  readonly marks = signal({ bold: false, italic: false, underline: false, bullet: false, ordered: false, link: false });
  readonly history = signal({ undo: false, redo: false });
  readonly hintId = `rich-hint-${++nextRichTextId}`;
  readonly linkInputId = `rich-link-${nextRichTextId}`;

  constructor() {
    if (this.ngControl) this.ngControl.valueAccessor = this;
    effect(() => {
      const label = this.placeholder();
      const editor = this.editor;
      if (!editor || editor.isDestroyed) return;
      editor.view.dispatch(editor.state.tr.setMeta('addToHistory', false).setMeta('placeholderRefresh', label));
    });
  }

  ngAfterViewInit(): void {
    const describedBy = this.hint() ? this.hintId : '';
    this.editor = new Editor({
      element: this.surface().nativeElement,
      content: toEditorHtml(this.pending),
      editable: !this.disabled(),
      extensions: [
        StarterKit.configure({
          heading: false,
          blockquote: false,
          code: false,
          codeBlock: false,
          strike: false,
          horizontalRule: false,
          link: {
            openOnClick: false,
            autolink: true,
            linkOnPaste: true,
            defaultProtocol: 'https',
            HTMLAttributes: { rel: 'noopener noreferrer', target: '_blank' },
            isAllowedUri: (url, context) => context.defaultValidate(url) && isSafeUrl(url),
          },
        }),
        Placeholder.configure({ placeholder: () => this.placeholder() }),
      ],
      editorProps: {
        attributes: {
          class: 'rich-editor',
          role: 'textbox',
          'aria-multiline': 'true',
          ...(this.labelledBy() ? { 'aria-labelledby': this.labelledBy() } : {}),
          ...(describedBy ? { 'aria-describedby': describedBy } : {}),
          ...(this.required() ? { 'aria-required': 'true' } : {}),
          spellcheck: 'true',
        },
      },
      onUpdate: () => this.emitHtml(),
      onSelectionUpdate: () => this.syncState(),
      onCreate: () => this.syncState(),
    });
    this.editor.view.dom.addEventListener('blur', () => {
      this.onTouched();
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy(): void {
    this.editor?.destroy();
    this.editor = null;
  }

  writeValue(value: string | null): void {
    this.lastHtml = storedHtml(toEditorHtml(value));
    if (!this.editor) {
      this.pending = value ?? '';
      return;
    }
    if (storedHtml(this.editor.getHTML()) === this.lastHtml) return;
    this.editor.commands.setContent(toEditorHtml(value), { emitUpdate: false });
    this.syncState();
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
    this.editor?.setEditable(!isDisabled);
  }

  showError(): boolean {
    const control = this.ngControl?.control;
    return !!this.requiredMessage() && !!control && control.invalid && (control.touched || control.dirty);
  }

  command(name: EditorCommand): void {
    const editor = this.editor;
    if (!editor || this.disabled()) return;
    const chain = editor.chain().focus();
    if (name === 'bold') chain.toggleBold().run();
    else if (name === 'italic') chain.toggleItalic().run();
    else if (name === 'underline') chain.toggleUnderline().run();
    else if (name === 'bulletList') chain.toggleBulletList().run();
    else if (name === 'orderedList') chain.toggleOrderedList().run();
    else if (name === 'undo') chain.undo().run();
    else if (name === 'redo') chain.redo().run();
    else chain.unsetAllMarks().clearNodes().run();
  }

  toggleLink(): void {
    const href = this.editor?.getAttributes('link')['href'];
    this.linkDraft.set(typeof href === 'string' && href ? href : 'https://');
    this.linkError.set(null);
    this.linkOpen.update((open) => !open);
  }

  applyLink(): void {
    const editor = this.editor;
    const href = this.linkDraft().trim();
    if (!editor || !isSafeUrl(href)) {
      this.linkError.set(this.i18n.t('El enlace debe empezar por http://, https:// o mailto:.'));
      return;
    }
    const { from, to } = editor.state.selection;
    if (from === to) {
      editor.chain().focus().insertContent({ type: 'text', text: href, marks: [{ type: 'link', attrs: { href } }] }).run();
    } else {
      editor.chain().focus().extendMarkRange('link').setLink({ href }).run();
    }
    this.linkError.set(null);
    this.linkOpen.set(false);
  }

  removeLink(): void {
    this.editor?.chain().focus().extendMarkRange('link').unsetLink().run();
    this.linkError.set(null);
    this.linkOpen.set(false);
  }

  private emitHtml(): void {
    const editor = this.editor;
    if (!editor) return;
    const html = storedHtml(editor.getHTML());
    if (html !== this.lastHtml) {
      this.lastHtml = html;
      this.onChange(html);
    }
    this.syncState();
  }

  private syncState(): void {
    const editor = this.editor;
    if (!editor) return;
    this.marks.set({
      bold: editor.isActive('bold'),
      italic: editor.isActive('italic'),
      underline: editor.isActive('underline'),
      bullet: editor.isActive('bulletList'),
      ordered: editor.isActive('orderedList'),
      link: editor.isActive('link'),
    });
    this.history.set({ undo: editor.can().undo(), redo: editor.can().redo() });
    const control = this.ngControl?.control;
    const invalid = !!control && control.invalid && (control.touched || control.dirty);
    editor.view.dom.setAttribute('aria-invalid', invalid ? 'true' : 'false');
    this.cdr.markForCheck();
  }
}

function storedHtml(html: string): string {
  return richTextPlain(html) ? html : '';
}
