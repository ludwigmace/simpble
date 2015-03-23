simpble
=======
SimpBLE is a framework for simple message passing over Bluetooth Low Energy I'm writing for my Master's report.  It will serve at the basis for a secure message drop app that will work among both Android and iOS devices.

Right now only the Android portion is in development but it shouldn't be too hard to get it moved to iOS (see the project I wrote with my teammate rsandoval for a <a href="https://github.com/ludwigmace/blemsgboard">simple BLE message board</a> in iOS).

Uses a 16 byte random IV for AES encryption as well as random 32 byte key.
