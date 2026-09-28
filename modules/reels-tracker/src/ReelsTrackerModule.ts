import { NativeModule, requireNativeModule } from 'expo';

import { ReelsTrackerModuleEvents } from './ReelsTracker.types';

declare class ReelsTrackerModule extends NativeModule<ReelsTrackerModuleEvents> {
  setValueAsync(value: string): Promise<void>;
}

export default requireNativeModule<ReelsTrackerModule>('ReelsTracker');
