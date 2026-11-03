# Release builds are shrunk with R8. The app uses no reflection, and OkHttp,
# Compose and coroutines ship their own keep rules, so nothing extra is needed.
# Add -keep rules here if a release build ever loses a class that a debug
# build has.
