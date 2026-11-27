import firebaseRulesPlugin from '@firebase/eslint-plugin-security-rules';

export default [
  {
    ignores: ['dist/**/*', 'functions/**/*', 'src/**/*', 'core/**/*', 'android/**/*', 'desktop/**/*'],
  },
  firebaseRulesPlugin.configs['flat/recommended'],
];
