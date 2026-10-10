import java.io.*;

public class scratch {
    public static void main(String[] args) throws Exception {
        Process process = new ProcessBuilder("/bin/bash").redirectErrorStream(true).start();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
        
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048]; int count;
                while ((count = reader.read(buffer)) != -1) {
                    System.out.println("STDOUT: " + new String(buffer, 0, count));
                }
            } catch (IOException exception) {
                System.out.println("Reader exception: " + exception.getMessage());
            }
            System.out.println("Reader thread exiting.");
        });
        readerThread.start();
        
        System.out.println("Process alive: " + process.isAlive());
        Thread.sleep(1000);
        System.out.println("Process alive after 1s: " + process.isAlive());
        
        writer.write("echo 'Hello'");
        writer.newLine();
        writer.flush();
        
        Thread.sleep(1000);
        System.out.println("Process alive after writing: " + process.isAlive());
        System.exit(0);
    }
}
