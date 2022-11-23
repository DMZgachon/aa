package chatting;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import database.*;

public class chating_server implements Runnable {

    public static ArrayList<connection> connection_list = new ArrayList<connection>();
    public static ArrayList<online_user> online_user_list = new ArrayList<online_user>();
    public static database db=  new database();
    private static class ConnectThread extends Thread
    {
        ServerSocket serverSocket;
        int count = 1;

        //조태완이 참가한 채팅방의 고유번호(md5) 다 검색을 해 db에서
       //채팅방의 수만큼 소켓을 저장하는 배열을 만들어야해
        //채팅방 고유번호 리스트



        ConnectThread (ServerSocket serverSocket) //생성자를 통해 서버소켓을 받음
        {
            System.out.println(" Server opened"); //서버가 열렸다는 메세지 출력
            this.serverSocket = serverSocket; //서버소켓을 저장
        }

        @Override
        public void run ()
        {
            try
            {
                while (true) //계속 새로운 클라이언트의 연결을 수락하고 새 소켓을 cLIENTtHREAD에 넘겨줌
                {
                    Socket socket = serverSocket.accept();  //클라이언트의 연결을 수락
                    System.out.println("    Thread " + count + " is started.");
                    chat_unit serverThread = new chat_unit(socket, count);
                    serverThread.start(); //새로운 클라이언트의 연결을 수락하고 새 소켓을 cLIENTtHREAD에 넘겨줌
                    count++;
                }
            } catch (IOException e)
            {
                System.out.println(e);
                System.out.println("    SERVER CLOSE    ");
            }
        }
    }
    @Override
    public void run(){
        ServerSocket serverSocket = null;
        try
        {   // 서버소켓을 생성, 25588 포트와 binding
            serverSocket = new ServerSocket(25588); // 생성자 내부에 bind()가 있고, bind() 내부에 listen() 있음
            ConnectThread connectThread = new ConnectThread(serverSocket); // 서버소켓을 connectThread에 넘겨줌
            connectThread.start(); // connectThread 시작


        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }




    private static class chat_unit extends Thread
    {
        Socket socket;
        int id;


        //생성자를 통해 입력받은 소켓과 클라이언트(쓰레드)의 id를 저장
        chat_unit(Socket socket, int id)
        {
            this.socket = socket;
            this.id = id;
        }

        public String getServerDateTime(){
            String DateTime=null;
            LocalTime now = LocalTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH_mm_ss");
            DateTime = now.format(formatter);
            return DateTime;

        }



        @Override
        public void run ()
        {
            protocol content = null;

            try {
                 InputStream is = socket.getInputStream();
                 ObjectInputStream ois= new ObjectInputStream(is);
                 DataInputStream dis = new DataInputStream(is);
                 OutputStream os = socket.getOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(os);

                 int user_id= dis.readInt();
                 String room_id=null;
                 //클라이언트를 채팅 온라인 유저 목록 저장 리스트에 등록
                 online_user user = new online_user(user_id,socket);
                 if(online_user_list.contains(user)){
                     System.out.println("이미 서버에 온라인 상태로 등록된 유저.");
                 }else{
                        online_user_list.add(user);
                        System.out.println("유저: "+ user.user_id +"가 서버에 온라인 상태로 등록되었습니다.");
                 }

                 //user_id 이용해서 속해 있는 room_id를 db에서 찾은후 커넥션 리스트에 등록하기
                 ArrayList<String> room_id_list = db.get_users_room(user_id);
                 if(room_id_list.size()==0){
                     System.out.println("해당 유저가 속한 채팅방이 없습니다.");
                 }
                 else{
                     for(int i=0; i<room_id_list.size(); i++){
                         room_id=room_id_list.get(i);
                         connection tmp = new connection(room_id,user_id,socket);
                         if(connection_list.contains(tmp)){

                         }else{
                             connection_list.add(tmp);
                         }

                     }
                 }

                 while ((content = (protocol) ois.readObject()) != null) {
                     if (content.getTypeofrequest() == 1) { //새 방 만들기 요청
                         if (db.newroom(content) == true) { //db에 방만들기 요청
                             ArrayList<Integer> new_user_list = content.getList(); //새로 만들어진 방에 들어갈 유저들의 id
                             protocol update_req = new protocol(5, -1); //새로 만들어진 방에 들어갈 유저들에게 새로운 방이 생겼다고 알려주는 요청
                             for (int i = 0; i < new_user_list.size(); i++) {
                                 for (int j = 0; j < online_user_list.size(); j++) {
                                     if (new_user_list.get(i) == online_user_list.get(j).user_id) {
                                         System.out.println("새로운 방이 생겼습니다. 유저: " + new_user_list.get(i) + "에게 알려줍니다.");
                                         Socket tmp_socket = online_user_list.get(j).socket;
                                         OutputStream tmp_os = tmp_socket.getOutputStream();
                                         ObjectOutputStream tmp_oos = new ObjectOutputStream(tmp_os);
                                         tmp_oos.writeObject(content); // update_req
                                         tmp_oos.flush();
                                     }
                                 }
                             }
                             System.out.println("새 방 만들기 성공");
                         } else {
                             System.out.println("새 방 만들기 실패");
                         }

                     } else if (content.getTypeofrequest() == 2) { //방에 유저 초대

                     } else if (content.getTypeofrequest() == 3) { //방 제거

                     } else if (content.getTypeofrequest() == 4) { //메시지 보내기
                         room_id = content.getRoomnumber();
                         connection tmp = new connection(room_id, user_id, socket);
                         if (connection_list.contains(tmp)) {
                             System.out.println("이미 연결정보에 등록됨");
                             for (int i = 0; i < connection_list.size(); i++) {
                                 if (connection_list.get(i).room_id.equals(room_id)) {
                                     System.out.println("방에 있는 사람들에게 메세지 전송");
                                     Socket temp_socket = connection_list.get(i).socket;
                                     ObjectOutputStream temp_oos = new ObjectOutputStream(temp_socket.getOutputStream());
                                     temp_oos.writeObject(content);
                                     temp_oos.flush();
                                 }
                             }
                         } else {

                             System.out.println("이미 연결정보에 등록됨");
                             for (int i = 0; i < connection_list.size(); i++) {
                                 if (connection_list.get(i).room_id.equals(room_id) && connection_list.get(i).user_id != user_id) {
                                     System.out.println("방에 있는 사람들에게 메세지 전송");
                                     Socket temp_socket = connection_list.get(i).socket;
                                     ObjectOutputStream temp_oos = new ObjectOutputStream(temp_socket.getOutputStream());
                                     temp_oos.writeObject(content);
                                     temp_oos.flush();
                                 }
                             }
                         }
                     } else if (content.getTypeofrequest() == 5) { //방목록 업데이트 요청인경우
                         int update_user_id = content.getSender();
                         ArrayList<String> room_list = db.get_room_list(update_user_id);
                         protocol response = new protocol(6, room_list);
                         ObjectOutputStream tmp_oos = new ObjectOutputStream(os);
                         tmp_oos.writeObject(response);
                         tmp_oos.flush();

                     } else {
                         System.out.println("잘못된 요청입니다.");
                     }
                 }
                     ois.close();
                     socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }



//ClientList