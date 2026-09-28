import { registerWebModule, NativeModule } from 'expo';

import { ReelsTrackerModuleEvents } from './ReelsTracker.types';

// ReelsTrackerModule is not available on the web platform.
class ReelsTrackerModule extends NativeModule<ReelsTrackerModuleEvents> {}

export default registerWebModule(ReelsTrackerModule, 'ReelsTrackerModule');
