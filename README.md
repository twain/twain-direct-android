# TWAIN Direct Android Sample
A simple Android app that scans images using the TWAIN Direct protocol. Intended as a developer sample, the app is light on usability but demonstrates the key aspects of acquiring images using TWAIN Direct:

* Discovering scanners.
* Creating a TWAIN Direct session.
* Sending a task configuration to the scanner.
* Starting a scan.
* Transferring image blocks.
* Assembling blocks into a final PDF.

The TWAIN Direct protocol code is in a separate module from the Android UI. It would be easy to extract and use in any Java project.

## Getting Started
This is an Android Studio project. Open it in Android Studio and run it on your Android device.
Note that TWAIN Direct uses mDNS to discover scanners, and the service discovery doesn't work in the Android emulator. You'll need to run this on a real Android device to discover TWAIN Direct scanners on your network.

## Running the tests
There is currently minimal test coverage. You can run the tests in Android Studio.

## Built With
* [TinyDNSSD]([http://www.dropwizard.io/1.0.2/docs/]) - mDNS library used for reading TXT records on Android.

## License
License TBD



