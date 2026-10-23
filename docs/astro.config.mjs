import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// GitHub Pages project site: https://soufianodev.github.io/XtCrash/
export default defineConfig({
  site: 'https://soufianodev.github.io',
  base: '/XtCrash',
  trailingSlash: 'always',
  integrations: [
    starlight({
      title: 'XtCrash',
      description: 'Crash recovery and custom error screens for Android apps.',
      sidebar: [
        {
          label: 'Getting started',
          items: ['introduction', 'quickstart'],
        },
        {
          label: 'Guides',
          items: [
            'configuration',
            'custom-error-screen',
            'compose',
            'material-3-theming',
          ],
        },
      ],
      customCss: ['./src/styles/custom.css'],
    }),
  ],
});
