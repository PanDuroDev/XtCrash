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
      lastUpdated: true,
      editLink: { baseUrl: 'https://github.com/SoufianoDev/XtCrash/edit/master/docs/' },
      head: [
        { tag: 'meta', attrs: { property: 'og:type', content: 'website' } },
        { tag: 'meta', attrs: { property: 'og:site_name', content: 'XtCrash' } },
        {
          tag: 'meta',
          attrs: {
            property: 'og:image',
            content: 'https://soufianodev.github.io/XtCrash/xtcrash-banner.svg',
          },
        },
        { tag: 'meta', attrs: { name: 'twitter:card', content: 'summary_large_image' } },
      ],
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
          ],
        },
      ],
      customCss: ['./src/styles/custom.css'],
    }),
  ],
});
