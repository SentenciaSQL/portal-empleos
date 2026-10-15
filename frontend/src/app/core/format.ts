export function apiError(error: unknown): string {
  const body = (error as { error?: { message?: string; errors?: { message: string }[] } })?.error;
  if (body?.errors?.length) {
    return body.errors.map((item) => item.message).join(' ');
  }
  return body?.message || 'No se pudo completar la acción.';
}

export function formatDate(value?: string | null): string {
  if (!value) return '';
  return new Intl.DateTimeFormat('es-DO', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value));
}

const labels: Record<string, string> = {
  ONSITE: 'Presencial',
  HYBRID: 'Híbrida',
  REMOTE: 'Remota',
  INDEFINITE: 'Indefinido',
  TEMPORARY: 'Temporal',
  PROJECT: 'Por proyecto',
  INTERNSHIP: 'Pasantía',
  FREELANCE: 'Freelance',
  FULL_TIME: 'Tiempo completo',
  PART_TIME: 'Medio tiempo',
  HOURLY: 'Por horas',
  NONE: 'Sin experiencia',
  JUNIOR: 'Junior',
  MID: 'Intermedio',
  SENIOR: 'Senior',
  LEAD: 'Liderazgo',
  HOUR: 'hora',
  DAY: 'día',
  WEEK: 'semana',
  BIWEEKLY: 'quincena',
  MONTH: 'mes',
  YEAR: 'año',
  ONSITE_ONLY: 'Presencial',
  NATIONAL: 'Remoto en el país',
  INTERNATIONAL: 'Remoto internacional',
  RECEIVED: 'Recibida',
  IN_REVIEW: 'En revisión',
  SHORTLISTED: 'Preseleccionada',
  INTERVIEW: 'Entrevista',
  OFFER: 'Oferta laboral',
  HIRED: 'Contratada',
  REJECTED: 'Rechazada',
  WITHDRAWN: 'Retirada',
  DRAFT: 'Borrador',
  PUBLISHED: 'Publicada',
  PAUSED: 'Pausada',
  CLOSED: 'Cerrada',
  EXPIRED: 'Vencida',
  PENDING: 'Pendiente',
  ACTIVE: 'Activa',
  SUSPENDED: 'Suspendida',
  VERIFIED: 'Verificada',
  BASIC: 'Básico',
  INTERMEDIATE: 'Intermedio',
  ADVANCED: 'Avanzado',
  NATIVE: 'Nativo',
  OPEN: 'Abierto',
  REVIEWED: 'Revisado',
  DISMISSED: 'Descartado',
};

export function label(value?: string | null): string {
  if (!value) return '';
  return labels[value] ?? value;
}

export function money(amount: number, currency = 'DOP'): string {
  return new Intl.NumberFormat('es-DO', { style: 'currency', currency, maximumFractionDigits: 0 }).format(amount);
}

export function salaryText(job: {
  salaryMin?: number | null;
  salaryMax?: number | null;
  salaryCurrency?: string | null;
  salaryPeriod?: string | null;
  salaryNegotiable?: boolean;
}): string {
  const currency = job.salaryCurrency || 'DOP';
  const period = job.salaryPeriod ? label(job.salaryPeriod) : '';
  let base = '';
  if (job.salaryMin != null && job.salaryMax != null) base = `${money(job.salaryMin, currency)} – ${money(job.salaryMax, currency)}`;
  else if (job.salaryMin != null) base = `Desde ${money(job.salaryMin, currency)}`;
  else if (job.salaryMax != null) base = `Hasta ${money(job.salaryMax, currency)}`;
  if (!base) return job.salaryNegotiable ? 'Salario negociable' : 'Salario no indicado';
  const suffix = period ? ` / ${period}` : '';
  return job.salaryNegotiable ? `${base}${suffix} · negociable` : `${base}${suffix}`;
}

export function locationText(job: { municipality?: string | null; province?: string | null; locationLabel?: string | null; countryName?: string | null; workMode?: string }): string {
  if (job.locationLabel) return job.locationLabel;
  if (job.municipality && job.province) return `${job.municipality}, ${job.province}`;
  if (job.province) return job.province;
  if (job.workMode === 'REMOTE') return job.countryName ? `Remoto · ${job.countryName}` : 'Remoto';
  return job.countryName || 'República Dominicana';
}
