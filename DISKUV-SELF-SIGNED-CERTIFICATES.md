# Self Signed Certificates

> The Android client uses OkHttp, which requires that SSL certificates have a "Subject Alternative Name" (SAN).
> Most online tutorials for creating your SSL certificate will forget to include the SAN.

1. We'll be using openssl and Java keytool to generate the certificate.
   So create an openssl configuration file `/tmp/req.conf`:

    ```text
    [req]
    distinguished_name = req_distinguished_name
    x509_extensions = v3_req
    prompt = no
    [req_distinguished_name]
    C = US
    ST = WA
    O = Starbucks
    OU = Coffee
    CN = 192.168.1.100
    [v3_req]
    subjectAltName = @alt_names
    [alt_names]
    IP.1 = 192.168.1.100
    #DNS.1 = mycomputer
    ```

2. Edit that configuration so that your CN and IP.1 are your IP address.
   Do not use your loopback adapter address (127.0.0.1).

   Alternative: We don't recommend this option, but you can use DNS.1 instead of IP.1.
   You have to figure out yourself how to get your Android/iOS client to recognize your
   computer's domain name (`mycomputer` in the example above).

3. Run:

   ```bash
   openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /tmp/certandkey.pem -out /tmp/certandkey.pem -config /tmp/req.conf -extensions v3_req
   openssl pkcs12 -export -in /tmp/certandkey.pem -inkey /tmp/certandkey.pem -name local-server -out /tmp/server.p12 -passout pass:
   keytool -importkeystore -srcstorepass '' -deststorepass diskuv -destkeystore /tmp/server.jks -srckeystore /tmp/server.p12 -srcstoretype PKCS12
   ```

4. Let's assume you already have a YAML configuration file (ex.
   `java -jar configurator/target/configurator-*.jar` generate). In the YAML file
   you may add or edit the server/applicationConnectors/https (or h2) section to include your
   certificate, like so:

    ```yaml
    server:
      applicationConnectors:
        - type: https
          port: 9443
          keyStorePath: /tmp/server.jks
          keyStorePassword: diskuv
    ```

5. Follow the instructions in Diskuv-Communicator-Android's
   `DISKUV-DEVELOPING.md`. There is a section "Connecting to a private test server"
   that we recommend following.

   Alternative: There is a section "Regenerating the trust store". You can add your
   certificate within its `certificateauthorities` variable by copying your `/tmp/certandkey.pem`
   to Diskuv-Communicator-Android's app/certs/ folder. Then follow the shell commands
   from that section.
