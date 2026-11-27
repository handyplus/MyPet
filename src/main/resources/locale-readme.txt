This folder is only for your OWN MyPet translation files!

The default translation is included into the plugin file itself:

  MyPet_zh.properties            Chinese (Simplified) - the reference and fallback

Only this Chinese file ships with this build. All other languages were removed,
and every key that is not translated falls back to Simplified Chinese.

To use another language put a MyPet_<locale>.properties file into this folder
(plugins/MyPet/locale/). Missing keys in that file fall back to Simplified
Chinese as well.

To update the bundled file, copy MyPet_zh.properties into src/main/resources/locale.

If you are interested in translating MyPet visit:

https://translation.mypet-plugin.de/
