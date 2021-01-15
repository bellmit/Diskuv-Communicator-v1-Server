# Threat Model

Here we discuss some key parts of the Diskuv survivor threat model that are different from Signal's threat model. These threats are _not_ knocks on the security of Signal, because Signal was designed for a different threat model than Diskuv.

- **TM1**: Chuck has possession of Bob's phone for several minutes. Bob is a survivor. Chuck is an assailant.
  Chuck opens Bob's Diskuv Communicator application and enters contact numbers for people he has assaulted.
  One of those people he has assaulted is Alice, who is not known by Bob.
  Chuck sees that Alice is a registered user. Now Alice is on Chuck's radar.

  Vulnerability: With Signal as-is, this threat is trivial to execute because of the [public nature of Signal contact discovery][1].
  In fact, Chuck does not even need Bob's phone; he can simply hash Alice's phone number and check the Signal server
  API /v1/discovery/{hash}.

  Mitigation: Diskuv Communicator will only authorize survivors to use the APIs. Chuck will now need a logged-in
  Diskuv Communicator or compromised login credentials to continue this threat.

  Mitigation: As per [the Hagen research paper][1], "Mutual Contacts" is one mitigation when Chuck has
  Bob's phone. Chuck cannot see that Alice is a registered user because Alice does not have Bob on her phone's contact list.
  A similar mitigation is that Alice and Bob must have explicitly "friended" each other.
  
# User Facing Changes

- Safety Goal, **TM1**: All communication with a
[Contact Discovery Service](https://github.com/signalapp/ContactDiscoveryService#readme)
is removed.

# Footnotes

[1]: https://encrypto.de/papers/HWSDS21.pdf 'Christoph Hagen, Christian Weinert, Christoph Sendner, Alexandra Dmitrienko, Thomas Schneider. "All the Numbers are US: Large-scale Abuse of Contact Discovery in Mobile Messengers". University of Würzburg and Technical University of Darmstadt.'
