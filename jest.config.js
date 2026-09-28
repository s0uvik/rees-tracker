/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-expo',
  // Fixed zone with DST so boundary tests are deterministic on any machine.
  globalSetup: '<rootDir>/jest.global-setup.js',
  testPathIgnorePatterns: ['/node_modules/', '/android/', '/ios/'],
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    '^reels-tracker$': '<rootDir>/modules/reels-tracker',
  },
};
