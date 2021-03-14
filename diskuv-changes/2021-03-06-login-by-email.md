# Login By Email

## Introduction

This is described in more detail in Diskuv-Communicator-Android/diskuv-changes/2021-03-06-login-by-email.md.

## Server Ramifications

This is a major change that breaks compatibility with Signal Server.
That is, an ordinary Signal Android/iOS client will not be able to communicate with
Diskuv Communicator Server after this.

Why did we break API compatibility?
1. We didn't want any question that we were storing phone numbers. That makes it much easier to audit
   that the server is not storing personally identifiable information, and aids transparency with
   any user who is technically capable of understanding 3rd party source code.
2. We had little choice.
   * The database had a unique constraint on phone numbers, which meant we either supply
     random phone numbers (making reason #1 harder) or drop the unique constraint. The latter was the
     clean choice, so we chose that.
   * The preauth API and the SMS/voice verification API included `/{number}` as part of the API path. It makes most
     sense to remove those APIs so that phone numbers are not inadvertently transmitted.

The following change(s) broke API compatibility:

* Added changesets `16+diskuv-1` and `16+diskuv-2` in `service/src/main/resources/accountsdb.xml` which
  drop the old unique constraints and add the new UUID unique constraint
* The authenticators have changed. Instead of just the device password authentication of
  `BaseAccountAuthenticator`, we additionally verify a JWT token with email address claims.
  Specifically, the device password authentication moved from the `Authorization: Basic ...` header
  to `X-Diskuv-Device-Authorization: Basic ...`, and the JWT token is `Authorization: Bearer ...`.
  For websockets, the old `/v1/websocket/?login=...&password=...` URL parameters used by device
  password authentication has been replaced with
  `/v1/websocket/?device-id=...&device-password=...&jwt-token=...`
* The APIs `/v1/accounts/{type}/preauth/{token}/{number}` and `/v1/accounts/{transport}/code/{number}`
  have been removed. Since we can always authenticate with JWT tokens, we've made a new pre-registration
  API that generates the CAPTCHA-skipping "push challenge": `/v1/accounts/{type}/prereg/{token}`
* The verification code API `PUT /v1/accounts/code/{verification_code}` has been removed. There is no
  need for a human verification code when we can verify the JWT tokens. Since that API finalized the
  account, we replaced it with `PUT /v1/accounts/account`. Similar to the Signal API
  `/v1/accounts/{transport}/code/{number}` that was removed, in our new `PUT /v1/accounts/account` API
  we ask for both the push challenge and the captcha; one or the other must be set.

The following significantly changed semantics:
* The API 'PUT /v1/messages/{destination}' handled by MessageController.sendMessage expects an JWT + device authenticated
  sender, unlike Signal which can rely purely on anonymous access keys. So Diskuv will, if someone were to mine the logs,
  know who sent the "unidentified" message. And even though, just like Signal, the recipient does not have the
  "source" of the unidentified message, there is (or will be) a change to Diskuv Communicator profiles that include
  email addresses. So the recipient will see the source email address through the sender profile.
  Net effect: Unidentified messages are essentially unsupported in Diskuv Communicator.
