// Re-export the native module. On web, it will be resolved to ReelsTrackerModule.web.ts
// and on native platforms to ReelsTrackerModule.ts
export { default } from './src/ReelsTrackerModule';
export * from './src/ReelsTracker.types';
