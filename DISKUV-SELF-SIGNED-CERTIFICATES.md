# Self Signed Certificates

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
    ```

2. Edit that configuration so that your CN and IP.1 are your IP address.
   Do not use your loopback address (127.0.0.1).

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

5. Then your server will be compatible with the Signal-Android code base, which requires through the
   `OkHostnameVerifier` that you have a certificate with a Subject Alternative Name (`IP.1` in the
   first step, or `DNS.1` if you use domain name certificates). You will still need to add your certificate to
   `app/src/main/res/raw/whisper.store` of Diskuv-Communicator-Android; instructions are in that
   project's DISKUV-DEVELOPING.md

