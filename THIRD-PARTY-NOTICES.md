# Third-party data sources

`src/main/resources/materials/zh_cn.tsv` is generated for the Paper 1.12.2
material set from these versioned sources:

- [LocaleLib 4.1.5](https://github.com/PikaMug/LocaleLib), whose legacy
  `Material + durability` key mapping is released under the MIT License.
- [minecraft-assets 1.12.2](https://github.com/InventivetalentDev/minecraft-assets),
  whose `assets/minecraft/lang/zh_cn.lang` values provide the Chinese display
  names.

The server does not need to install LocaleLib. Lib ships the generated catalog
so dependent plugins can use one stable data source without adding a runtime
dependency compiled for another Bukkit API version.
