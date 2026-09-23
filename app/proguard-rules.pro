-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*
-keep class io.wiggle.data.** { *; }

# Glance writes the GlanceAppWidget's class name into its own DataStore and looks the widget up by
# that name later, and it creates every ActionCallback by reflection. R8 renames both, and it picks
# different names on every build, so the name saved by one build no longer resolves in the next:
# updateAll then finds no widgets, returns without error, and the widget sits on its initial layout
# for good. Keeping the package costs a few classes and removes the whole failure mode.
-keep class io.wiggle.widget.** { *; }
