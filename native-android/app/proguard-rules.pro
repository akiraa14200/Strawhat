# Android does not provide java.beans APIs. SnakeYAML references these desktop-only
# classes for optional JavaBeans introspection; Android builds use the supported paths.
-dontwarn java.beans.**

# Add narrowly scoped keep rules here only when release testing identifies a reflection/JNI requirement.
