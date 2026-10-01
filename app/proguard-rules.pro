# Keep the Shizuku UserService class/constructors stable if minification is enabled later.
-keep class com.opendex.launcher.shell.UserShellService { *; }
-keep interface com.opendex.launcher.IUserShellService { *; }
