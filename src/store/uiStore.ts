import { create } from 'zustand';

import type { Period } from '@/features/stats/types';

type UiState = {
  period: Period;
  setPeriod: (p: Period) => void;
  /** null until first checked. */
  serviceEnabled: boolean | null;
  setServiceEnabled: (v: boolean) => void;
  /** User chose "skip for now" on onboarding; drives the persistent banner. */
  onboardingSkipped: boolean;
  setOnboardingSkipped: (v: boolean) => void;
};

export const useUiStore = create<UiState>((set) => ({
  period: 'daily',
  setPeriod: (period) => set({ period }),
  serviceEnabled: null,
  setServiceEnabled: (serviceEnabled) => set({ serviceEnabled }),
  onboardingSkipped: false,
  setOnboardingSkipped: (onboardingSkipped) => set({ onboardingSkipped }),
}));
