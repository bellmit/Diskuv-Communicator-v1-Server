# No Contact Discovery

## Introduction

This is described in more detail in Diskuv-Communicator-Android/diskuv-changes/2021-02-15-no-contact-discovery.md

## Server Ramifications

This is a major change that breaks compatibility with Signal Server.
That is, an ordinary Signal Android/iOS client will not be able to communicate with
Diskuv Communicator Server after this.

Specifically, any use of the `/v1/directory` APIs will fail with HTTP 404 Not Found.

Why did we break API compatibility?
1. It is fairly easy to disable the use of the Contact Discovery Service in the clients `/v1/directory`,
   so this isn't a one-way door. Said another way, it should be easy to contribute a change to the
   upstream Signal code to disable the use of the Contact Discovery Service behind a flag (perhaps compile-time
   or even a feature flag). Note: At the moment I don't understand why either Signal or Diskuv would waste time
   doing that. Regardless, it is trivially possible.
2. Fewer APIs means less attack exposure.

The following change(s) broke API compatibility:

* Deleted `service/src/main/java/org/whispersystems/textsecuregcm/controllers/DirectoryController.java`

Other important changes were:

* Deleted