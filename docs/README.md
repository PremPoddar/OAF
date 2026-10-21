# OAF website

The public site for OAF, served by GitHub Pages straight out of this folder.

| File | URL |
|---|---|
| `index.html` | https://prempoddar.github.io/OAF/ |
| `privacy/index.html` | https://prempoddar.github.io/OAF/privacy/ |

## Enabling Pages

In this repository: **Settings → Pages → Source: Deploy from a branch → Branch: `main`, folder: `/docs`**.
Give it a minute, then open https://prempoddar.github.io/OAF/.

`.nojekyll` is present so files are published as they are, without Jekyll.

## Downloads

The download buttons open `https://github.com/PremPoddar/OAF/releases/latest`. Publish a
release with the APK attached (**Releases → Draft a new release**) and they lead straight to it.

## Editing

Plain HTML and CSS with no build step: edit and push. `site.css` is shared by both pages; the
colours at the top of each page match the app, and the demo's markup, styles and script sit at the
end of `index.html`. If the app's behaviour changes, update `privacy/index.html` with it.
