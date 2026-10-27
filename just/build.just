android_java_home := "/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Build the iOS Swift package.
[group('build')]
build_ios args="":
  swift build {{ args }}

# Run the iOS unit tests.
[group('build')]
test_ios args="":
  swift test {{ args }}

# Build the Android library in release mode.
[group('build')]
build_android args="":
  cd android && JAVA_HOME={{ quote(android_java_home) }} PATH="{{ android_java_home }}/bin:$PATH" ./gradlew :biangbiang-ui:assembleRelease {{ args }}

# Run the Android unit tests.
[group('build')]
test_android args="":
  cd android && JAVA_HOME={{ quote(android_java_home) }} PATH="{{ android_java_home }}/bin:$PATH" ./gradlew test {{ args }}

# Run the Android lint checks.
[group('build')]
lint_android args="":
  cd android && JAVA_HOME={{ quote(android_java_home) }} PATH="{{ android_java_home }}/bin:$PATH" ./gradlew lintDebug {{ args }}

# Build both platforms.
[group('build')]
build:
  just build_ios
  just build_android

# Run the unit tests of both platforms.
[group('build')]
test:
  just test_ios
  just test_android
