import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.Base64;
import java.util.UUID;

/** Run with an existing JDK 17+; certificates are local test artifacts, never production credentials. */
public class CreateTestCertificate {
    public static void main(String[] args) throws Exception {
        Path directory=Paths.get(args[0]).toAbsolutePath().normalize(); Files.createDirectories(directory);
        Path store=directory.resolve("temporary-"+UUID.randomUUID()+".p12");
        String password=UUID.randomUUID().toString();
        try {
            String tool=Paths.get(System.getProperty("java.home"),"bin","keytool.exe").toString();
            Process process=new ProcessBuilder(tool,"-genkeypair","-alias","test","-keyalg","RSA","-keysize","2048",
                    "-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1","-storetype","PKCS12",
                    "-keystore",store.toString(),"-storepass",password,"-keypass",password,"-validity","7")
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            if (process.waitFor()!=0) throw new IllegalStateException("Test certificate creation failed");
            KeyStore keys=KeyStore.getInstance("PKCS12");
            try (java.io.InputStream in=Files.newInputStream(store)) {keys.load(in,password.toCharArray());}
            pem(directory.resolve("certificate.pem"),"CERTIFICATE",keys.getCertificate("test").getEncoded());
            pem(directory.resolve("private-key.pem"),"PRIVATE KEY",keys.getKey("test",password.toCharArray()).getEncoded());
        } finally {Files.deleteIfExists(store);}
    }
    private static void pem(Path path,String type,byte[] bytes) throws Exception {
        String text="-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(bytes)+
                "\n-----END "+type+"-----\n";
        Files.write(path,text.getBytes(StandardCharsets.US_ASCII));
    }
}
