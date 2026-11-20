const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const [src, out] = process.argv.slice(2);
const boards = [
  ['Main.dc.html', '01-home'], ['HomeFavorites.dc.html', '02-home-favorites'], ['HomeScroll.dc.html', '03-home-full-scroll'],
  ['EditWidgets.dc.html', '04-edit-widgets'], ['WidgetPicker.dc.html', '05-add-widget'], ['AppMenu.dc.html', '06-app-menu'],
  ['Settings.dc.html', '07-settings'], ['FontPicker.dc.html', '08-font-chooser'],
  ['CustomScreen.dc.html', '09-custom-screen'], ['ScreenSheet.dc.html', '10-screen-options'],
  ['ClearDialog.dc.html', '11-clear-apps'], ['EmptyScreen.dc.html', '12-empty-screen'],
  ['AppPicker.dc.html', '13-choose-apps'], ['TextEditor.dc.html', '14-text-label'],
  ['Monitor.dc.html', '15-phone-monitor'],
  ['Alignment.dc.html', '16-alignment'], ['SmartWidgets.dc.html', '17-smart-widgets'],
  ['AddScreen.dc.html', '18-add-screen'], ['ScreenLook.dc.html', '19-screen-look'],
  ['Toolbar.dc.html', '20-widget-toolbar'],
  ['FoldersDots.dc.html', '21-folders-dots'], ['MonitorMore.dc.html', '22-monitor-history'],
  ['NavStyles.dc.html', '23-switcher-styles'], ['NavMotion.dc.html', '24-switcher-motion'], ['ResizeUndo.dc.html', '25-resize-undo'], ['Pinboard.dc.html', '26-pinboard'], ['PhotoWidget.dc.html', '27-photo-widget'], ['WidgetAreas.dc.html', '28-widget-areas'], ['AppGroups.dc.html', '29-app-groups'], ['ShareCode.dc.html', '30-share-codes'], ['SavedSetups.dc.html', '31-saved-setups'], ['WidgetSlots.dc.html', '32-widget-placeholders'], ['Charging.dc.html', '33-charging-effects'], ['ChargingMore.dc.html', '34-more-charging-effects'], ['EdgeEffects.dc.html', '35-edge-effects'], ['TimerSky.dc.html', '36-timer-time-of-day'], ['MusicLive.dc.html', '37-music-live'], ['ScanShare.dc.html', '38-scan-to-import'], ['ShareSteps.dc.html', '39-share-and-scan-steps'], ['TutorialsLive.dc.html', '40-tutorials-live-code'],
];
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 2 });
  for (const [file, name] of boards) {
    const html = fs.readFileSync(path.join(src, file), 'utf8');
    const helmet = (html.match(/<helmet>([\s\S]*?)<\/helmet>/) || [, ''])[1];
    const body = html.match(/<x-dc>([\s\S]*?)<\/x-dc>/)[1].replace(/<helmet>[\s\S]*?<\/helmet>/, '');
    const logic = html.match(/<script type="text\/x-dc"[^>]*>([\s\S]*?)<\/script>/)[1];
    await page.setContent(`<!doctype html><html><head><meta charset="utf-8">${helmet}</head><body style="margin:0;background:#0F1012"><div id="tpl" hidden>${body}</div><div id="out"></div></body></html>`, { waitUntil: 'networkidle' });
    await page.evaluate((logic) => {
      class DCLogic { constructor() { this.props = {}; this.state = {}; } }
      const Component = new Function('DCLogic', logic + '\nreturn Component;')(DCLogic);
      const vals = new Component().renderVals();
      const get = (scope, p) => p.trim().split('.').reduce((o, k) => (o == null ? undefined : o[k]), scope);
      const fill = (s, scope) => s.replace(/\{\{([^}]+)\}\}/g, (_, p) => { const v = get(scope, p); return v == null ? '' : String(v); });
      const expand = (node, scope) => {
        for (const child of [...node.childNodes]) {
          if (child.nodeType === 3) { child.textContent = fill(child.textContent, scope); continue; }
          if (child.nodeType !== 1) continue;
          const tag = child.tagName.toLowerCase();
          if (tag === 'sc-for') {
            const list = get(scope, child.getAttribute('list').replace(/[{}]/g, '')) || [];
            const as = child.getAttribute('as');
            const frag = document.createDocumentFragment();
            list.forEach((item, i) => {
              const holder = document.createElement('div');
              holder.innerHTML = child.innerHTML;
              expand(holder, { ...scope, [as]: item, $index: i });
              frag.append(...holder.childNodes);
            });
            child.replaceWith(frag);
            continue;
          }
          for (const a of [...child.attributes]) if (a.value.includes('{{')) child.setAttribute(a.name, fill(a.value, scope));
          expand(child, scope);
        }
      };
      const tpl = document.getElementById('tpl');
      const holder = document.createElement('div');
      holder.innerHTML = tpl.innerHTML;
      expand(holder, vals);
      document.getElementById('out').append(...holder.childNodes);
      tpl.remove();
    }, logic);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(500);
    const root = await page.$('#out > *');
    await root.screenshot({ path: path.join(out, name + '.png') });
    console.log(name);
  }
  await browser.close();
})();
