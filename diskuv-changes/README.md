# README

## Threat Model

Here we discuss some key parts of the Diskuv member threat model that are different from Signal's threat model.
These threats are _not_ knocks on the security of Signal, because Signal was designed for a different
threat model than Diskuv.

- **TM0.a**: A compromised or malicious Diskuv employee, or an adversary who has compromised the
  Diskuv Communicator servers, accesses member's messages and videos.

  Mitigation: With Signal's end-to-end encryption, the employee has no access to a member's messages and videos.

- **TM0.b**: A Diskuv employee, or an adversary who has compromised the Diskuv Communicator servers,
  accesses members' metadata for financial extortion or to create negative publicity.

  Vulnerability: Even the limited metadata captured by Signal Server may be enough to use timing
  and correlation to discover who is talking to whom and when.

  Mitigation: Lock down and monitor the Diskuv Communicator servers.

  Mitigation: Limit metadata logs to 7 days or fewer. Seven days is sufficient for monitoring
  (the first mitigation) and DDOS protection and other abuse prevention.

  Mitigation: Limit the # of employees with access.

  Mitigation: Swamp member conversations with background noise. That is, encourage several
  uses of Diskuv Communicator to allow for plausible deniability.

  Future Mitigation: (Not distant future). Allow concerned parties to register on the user's behalf

  Future Mitigation: Keep Diskuv's fork in sync with Signal, since Signal has roadmap items to
  reduce the stored metadata (especially phone number).

- **TM1**: Chuck has possession of Bob's phone for several minutes. Bob is a member. Chuck is malicious.
  Chuck opens Bob's Diskuv Communicator application and enters contact numbers for people.
  One of those people he has been malicious to is Alice, who is not known by Bob.
  Chuck sees that Alice is a registered user. Now Alice is on Chuck's radar.

  Vulnerability: With Signal as-is, this threat is trivial to execute because of the [public nature of Signal contact discovery][1].
  In fact, Chuck does not even need Bob's phone; he can simply hash Alice's phone number and check the Signal server
  API /v1/discovery/{hash}.

  Mitigation: Diskuv Communicator will only authorize members to use the APIs. Chuck will now need a logged-in
  Diskuv Communicator or compromised login credentials to continue this threat. Members lose
  anonymity with the Diskuv server for the "metadata", so server logs and resources can be can be made to expire
  quickly.

  Mitigation: As per [the Hagen research paper][1], "Mutual Contacts" is one mitigation when Chuck has
  Bob's phone. Chuck cannot see that Alice is a registered user because Alice does not have Bob on her phone's contact list.
  A similar mitigation is that Alice and Bob must have explicitly "friended" each other.

- **TM2**: Chuck has possession of Bob's phone for several minutes.
  Alice and Bob are Diskuv peers. Alice does not want Chuck knowing Bob is a peer. And Alice and Bob are friends who are in each other's phone contacts and both have the Diskuv application, so Alice knows that Bob is a Diskuv peer and Bob knows Alice is a Diskuv peer. Chuck opens Bob's Diskuv Communicator application and sees that Alice is a Diskuv peer. Now Alice is on Chuck's radar.

  Vulnerability: With default Signal client settings, anyone with access to the phone can access the Signal messages.

  Mitigation: Enforce Android and iOS fingerprint/PIN locking before allowing access to Diskuv Communicator.
  Mitigation: Reduce blast radius by not keeping all messages forever on phone.

## User Facing Changes

- Safety Goal, **TM1**: All communication with a
[Contact Discovery Service](https://github.com/signalapp/ContactDiscoveryService#readme)
is removed.

## Footnotes

[1]: https://encrypto.de/papers/HWSDS21.pdf 'Christoph Hagen, Christian Weinert, Christoph Sendner, Alexandra Dmitrienko, Thomas Schneider. "All the Numbers are US: Large-scale Abuse of Contact Discovery in Mobile Messengers". University of Würzburg and Technical University of Darmstadt.'
