export interface Envelope<T> {
  success: boolean;
  data: T;
  message: string | null;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserSession {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: 'CANDIDATE' | 'EMPLOYER' | 'ADMIN';
  emailVerified: boolean;
  companyId: string | null;
  companyName: string | null;
  memberRole: string | null;
  locale?: string | null;
}

export interface AuthPayload {
  accessToken: string;
  expiresIn: number;
  user: UserSession;
}

export interface Named { id: number; name: string; code?: string; slug?: string; }
export interface Country { code: string; name: string; }
export interface Plan {
  id: number; code: string; name: string; description: string; priceAmount: number; currency: string;
  billingPeriod: string; maxActiveJobs: number; featuredCredits: number; candidateFilters: boolean;
  advancedStats: boolean; premiumProfile: boolean; active: boolean;
}

export interface JobCard {
  id: string; slug: string; title: string; companyName: string; companySlug: string; logoUrl: string | null;
  companyVerified: boolean; countryCode: string; countryName: string; province: string | null; municipality: string | null;
  locationLabel: string | null; workMode: string; contractType: string; workSchedule: string; experienceLevel: string;
  category: string; salaryMin: number | null; salaryMax: number | null; salaryCurrency: string; salaryPeriod: string | null;
  salaryNegotiable: boolean; acceptsNoExperience: boolean; remoteScope: string; geographicRestriction: string | null;
  publishedAt: string | null; featured: boolean; open: boolean;
}

export interface JobDetail {
  card: JobCard; description: string; requirements: string | null; responsibilities: string | null; status: string;
  expiresAt: string | null; viewCount: number; skills: string[]; saved: boolean; applied: boolean;
}
