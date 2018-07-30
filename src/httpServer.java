import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

public class httpServer implements Runnable
{
    private Socket client=null;
    /*homeDirectory is the given root directory for the server where it'll get hmtl and image files*/
    private static String homedirectory=null;
    private static int port=0;

    public httpServer(Socket client)
    {
        this.client=client;
    }

    public static void main(String[] args) throws IOException
    {
        /*Checking arguments, they should be exactly 2, one for portNumber the other one homedirectory*/
        if(args.length!=2)
        {
            System.out.println("Missing Arguments");
            System.exit(1);
        }

        port=Integer.parseInt(args[0]);
        homedirectory=args[1];

        ServerSocket server=null;

        try
        {
            /*Creating Server Socket*/
            server=new ServerSocket(port);
        }
        catch (IOException exc)
        {
            exc.printStackTrace();
        }

        while (true)
        {
            /*The loop that will keep continue until server is terminated manuelly*/
            Socket client=server.accept();
            new Thread(new httpServer(client)).start();
        }
    }


    @Override
    public void run()
    {
        try
        {
            /*BufferedReader to read from user agent and OutputStream,PrintStream to send user agent*/
            BufferedReader bufreader=new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream outStream=new BufferedOutputStream(client.getOutputStream());
            PrintStream printStream=new PrintStream(outStream);

            /*We read only the first line of user agent request because this line includes necessary information to get path*/
            String request=bufreader.readLine();
            /*A typical GET http request is GET / directory HTTP/version
             * to get the path from this get request we start from 4th char and dont read the last 9 char*/
            request=request.substring(4,request.length()-9).trim();

            /*If the given directory doesnt include an ending slash, we put it manually so we can handle the request
             * wheter there is an ending slash or not*/
            if (!request.endsWith("/"))
                request+="/";

            /*We concate the homedirectory and requested directory to get full path for folder/file*/
            String path=homedirectory+"/"+request;

            /*We use the boolean directory to save if the given path is a directory or not so we can use this information
            to detect errors(internal server, forbidden acces)*/
            boolean directory = false;
            path = path.replace("/index.html/", "/");
            File file=new File(path);

            /*if the requested path is directory we check .html and .htm to display if there is a such default file
             * under the directory*/
            if (file.isDirectory())
            {
                directory = true;
                path+="index.html/";
                file=new File(path);
                if (!file.isFile())
                {
                    path=path.replace("index.html/","index.htm/");
                    file=new File(path);
                }
            }

            /* We use count to check that user tries to acces root directory or any file beneath it */
            int count = 0;
            for(int i = 0; i < path.length(); i++)
            {
                if(path.charAt(i) == '/') {
                    count++;
                }
            }

            /*if the request from user agent is root, server responses with 403 Forbidden
            since the user doesnt have the permission*/
            // Using ".." in path allows users to acces root directory so we checked that to prevent any acces to root directory
            if(((request.indexOf("..")!= -1) || (count == 3)) && directory)
            {
                printStream.print("HTTP/1.0 " + 403 + " " + "FORBIDDEN" + "\r\n" + "\r\n" +
                        "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\r\n" + "<TITLE>" +
                        403 + " " + "FORBIDDEN" + "</TITLE>\r\n" + "</HEAD><BODY>\r\n" + "<H1>" +
                        "FORBIDDEN" + "</H1>\r\n" + "YOU HAVE NO PERMISSION FOR THIS DIRECTORY" +
                        "<P>\r\n" + "<HR><ADDRESS>FileServer 1.0 at " + client.getLocalAddress().getHostName() +
                        " Port " + client.getLocalPort() + "</ADDRESS>\r\n" + "</BODY></HTML>\r\n");
            }
            else
            {
                /* Files inside directory3(dir3) are currently under a maintenance so the user is redirected to directory1(dir1) */
                if ((path.toLowerCase().contains("dir3")) && (request.indexOf("dir3") == 1)) {
                    printStream.print("HTTP/1.0 302 FOUND\r\n" + "Location: /dir1" + "\r\n\r\n");
                }

                /*Here we open file with the path and send raw data to user agent with 200 http response*/
                try
                {
                    InputStream fromfile=new FileInputStream(file);
                    printStream.print("HTTP/1.0 200 OK\r\n" +contentType(request)+"\r\n"
                                                            +"Date:" + new Date()+"\r\n"
                                                            +"Server: MyHttpServer"+
                                                            "\r\n\r\n");
                    byte[] buffer = new byte[1024];
                    while (fromfile.available()>0)
                        outStream.write(buffer, 0, fromfile.read(buffer));
                }
                catch (FileNotFoundException f)
                {
                    if(directory)
                    {
                        printStream.print("HTTP/1.0 " + 500 + " " + "INTERNAL SERVER ERROR" + "\r\n" +
                                "\r\n" + "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\r\n" + "<TITLE>" +
                                500 + " " + "INTERNAL SERVER ERROR" + "</TITLE>\r\n" + "</HEAD><BODY>\r\n" + "<H1>" +
                                "INTERNAL SERVER ERROR" + "</H1>\r\n" + "SOMETHING WENT WRONG WITH THE SERVER" + "<P>\r\n" +
                                "<HR><ADDRESS>FileServer 1.0 at " + client.getLocalAddress().getHostName() +
                                " Port " + client.getLocalPort() + "</ADDRESS>\r\n" + "</BODY></HTML>\r\n");
                    }
                    else
                    {
                        printStream.print("HTTP/1.0 " + 404 + " " + "NOT FOUND" + "\r\n" + "\r\n" +
                                "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\r\n" + "<TITLE>" +
                                404 + " " + "NOT FOUND" + "</TITLE>\r\n" + "</HEAD><BODY>\r\n" + "<H1>" +
                                "NOT FOUND" + "</H1>\r\n" + "THE PAGE COULDNT BE FOUND" + "<P>\r\n" +
                                "<HR><ADDRESS>FileServer 1.0 at " + client.getLocalAddress().getHostName() +
                                " Port " + client.getLocalPort() + "</ADDRESS>\r\n" + "</BODY></HTML>\r\n");
                    }
                }
            }
            outStream.flush();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        if (client!=null)
        {
            try { client.close(); }
            catch (IOException e) { e.printStackTrace(); }
        }
    }

    /*This method returns the content type for 200 OK based on the given path*/
    private static String contentType(String path)
    {
        if (path.endsWith(".html/") || path.endsWith(".htm/"))
            return "Content-Type: text/html";
        else if (path.endsWith(".jpg/") || path.endsWith(".jpeg/"))
            return "Content-Type: image/jpeg";
        else if (path.endsWith(".png/"))
            return "Content-Type: image/png";
        return "text/html";
    }
}