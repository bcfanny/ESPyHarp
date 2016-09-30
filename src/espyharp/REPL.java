/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package espyharp;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;

import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import org.fxmisc.flowless.VirtualizedScrollPane;

import org.fxmisc.richtext.CodeArea;

/**
 * 23/Sep/2016 REPL을 구현하기 위한 클래스
 * @author salesiopark
 */
public class REPL {
    
    public static CodeArea caREPL = new CodeArea();
    public static int iPosCaretBlock = 0; // 커서의 맨 뒷 위치
    
    // caret의 현재 위치를 기록한다. codeArea.getCaretPosition() 로 구하자만
    // 만약 마우스클릭이벤터가 일어났다면 caret의 위치가 이미 변경되었으므로
    // 그 이전의 (올바른) 위치로 caret을 옮길 때 참조하는 변수다
    public static int iPosCaretCur = 0;
    
    public static int iPosCommStrt = 0; // 입력 시작 위치
    public static int iPosEnter = 0; // 엔터키를 누른 이후의 caret 위치
//    public static String strSent = ""; // 보낸 명령문
    
    public static PortReader listener = new PortReader();
    
    // '>>> '를 받고 'ENTER'를 누르기 전까지는 아래 flag는 true이다.
    // 이것이 true인 동안에는 다른 listener를 등록하여
    // 특정한 명령을 수행하는 것이 가능하다.
    private static boolean bIdleMode = false;
    
    // 최초 실행 후 파일리스트를 아직 읽지 않았음을 표시
    public static boolean bFileRead = false;
    
    /**
     * 케럿의 위치를 옮기고 iPosCaretCur 변수도 갱신한다.
     * @param iPos 
     */
    
      
    public static void init(AnchorPane apane) {
        caREPL = new CodeArea();
        caREPL.setWrapText(true);// 이렇게 하면 HScrollBar는 안 생긴다.
//        String stylesheet = ESPyHarp.class.getResource("caREPL.css").toExternalForm();
        String stylesheet = ESPyHarp.class.getResource("pyTab.css").toExternalForm();
        caREPL.getStylesheets().add(stylesheet);
        
        // 스크롤바를 붙이려면 아래와 같이 VirtualizedScrollPane 을 생성해서 붙여야 한다.
        VirtualizedScrollPane caREPLS = new VirtualizedScrollPane<>(caREPL);
        apane.getChildren().add( caREPLS );
        Util.setAnchors(caREPLS, 33, 0, 0, 0);
        
        //caREPL.setOnKeyPressed(new EventHandler<KeyEvent>() {
        //@Override public void handle(KeyEvent ke) {
        //caREPL.setOnKeyPressed( (KeyEvent ke) -> {
        caREPL.addEventFilter(KeyEvent.KEY_PRESSED, (ke) -> {
                iPosCaretCur = caREPL.getCaretPosition();
                KeyCode kc = ke.getCode();
                
                if ( iPosCaretCur <= iPosCaretBlock ) {
                    if (kc == KeyCode.BACK_SPACE || kc == KeyCode.LEFT ) {
                        ke.consume();
                    }
                }
                
                if ( kc == KeyCode.HOME ) {
                        moveCaretTo(iPosCaretBlock);
                        ke.consume();
                }
                
                if (kc == KeyCode.ENTER) {
                    bIdleMode = false;
                    iPosEnter = caREPL.getCaretPosition();
                    String str = caREPL.getText();
                    
                    System.out.println(String.format("strt:%d, len-1:%d", iPosCommStrt, str.length()-1));
                    
                    // 아래 if 문은 "...     " 입력문에서 backspace를 누른 후
                    // enter를 입력한 경우를 체크하는 것이다.
                    if ( iPosCommStrt >= str.length() ) {
                        System.out.println("no input");
//                        Uart.sendByte(10);//실패:6,8,12,14,27
                        Uart.exec();
                    } else {
                        // 타이핑된 문자들만 뽑아내서 그 끝에 '\r' 을 붙여서 전송한다.
                        String comm = str.substring(iPosCommStrt, str.length());

                        // 입력된 문자열을 먼저 모두(return까지 포함해서) 지운다.
                        // 보낸 문자열이 그대로 다시 오기 때문에 중복 표시를 막아준다.
                        caREPL.replaceText(iPosCommStrt, iPosEnter, "");
                        caREPL.moveTo(iPosCommStrt);
                        Uart.exec(comm);
                    }
                    ke.consume();
                }//if (kc == KeyCode.ENTER)
                
                // CTRL-D (ASCII:0x04) -- one a blank line, do a soft reset of the board
                if (ke.getCode() == KeyCode.D && ke.isControlDown()) {
//                    System.out.println("CTRL-D");
                    Uart.sendByte(4); // soft-reset
                } else if (ke.getCode() == KeyCode.C && ke.isControlDown()) {
                // CTRL-C (ASCII:0x03) -- interrupt a running program
                    Uart.sendByte(3);
                }
                
            });
        

        // codearea에서 어떤 곳을 클릭해도
        //  caret을 클릭한 곳 말고 정해진 위치로 놓는다.
        //  caREPL.setOnMousePressed(new EventHandler<MouseEvent>() {
        //  @Override public void handle(MouseEvent me) {
        caREPL.setOnMousePressed( (MouseEvent me) -> { // 위 코드를 람다로 변경
                if ( caREPL.getCaretPosition() < iPosCaretBlock ) {
                    moveCaretTo(iPosCaretCur);// not caREPL.positionTo()
                } else {
                    iPosCaretCur = caREPL.getCaretPosition();
                }
        });
        
        // 키워드 하이라이팅 (동기방식)
        // RichTextFx 의 java-keword 데모에서 복사함.
        caREPL.richChanges()
                .filter(ch -> !ch.getInserted().equals(ch.getRemoved())) // XXX
                .subscribe(change -> {
                    caREPL.setStyleSpans(0, KWH.computeHighlighting(caREPL.getText()));
        });

        caREPL.setDisable(true);
        
        /**
         * tab키를 " "*4 로 치환하는 필터 등록
         * addEventFilter함수를 사용해야 한다.
         * @see <a href="http://studymake.tistory.com/582">설명</a> 
         */
        caREPL.addEventFilter(KeyEvent.KEY_PRESSED, (keyEvent) -> {
            if (keyEvent.getCode() == KeyCode.TAB) {
                caREPL.insertText(caREPL.getCaretPosition(), "    ");
                keyEvent.consume();
            }
        });

    }
    
    public static void moveCaretTo(int iPos){
        caREPL.moveTo(iPos);// not caREPL.positionTo()
        iPosCaretCur = caREPL.getCaretPosition();
    }
    
    /**
     * event listener를 다시 가져와서 여기에서 처리한다.
     * @param strR REPL영역에서 표시할 문자열
     * 받은 문자열을 표시하고 명령 입력 모드로 진입한다.
    */
    public static void setListener(String strR){
        Uart.setListener(listener);
//        caREPL.appendText(strR);
        String str = caREPL.getText();
        iPosCaretBlock = iPosCommStrt = str.length();
        moveCaretTo(iPosCommStrt);
    }
    
    public static class PortReader implements SerialPortEventListener {

        @Override
        public void serialEvent(SerialPortEvent event) {
            if(event.isRXCHAR() && event.getEventValue() > 0) {
                try {
                    String receivedData = Uart.getPort().readString(event.getEventValue());
                    
                    //아래와 같이 하지 않으면 thread 에러가 발생한다.
                    // 현재 thread가 종료된 이후에 실행된다.
                    // Platform.runLater(new Runnable() {@Override public void run() {
                    Platform.runLater( () -> { // 윗 줄을 익명함수 이용 수정
                        caREPL.appendText(receivedData);
                        if (receivedData.contains(">>>")) {
                            String str = caREPL.getText();
                            iPosCaretBlock = iPosCommStrt = str.length();
                            moveCaretTo(iPosCommStrt);
                            bIdleMode = true;
                            
                            // 이 함수 종료후 다른 쓰레드에서 파일리스트를 읽어들인다.
                            if (!bFileRead) {
                                Platform.runLater(()->{
                                    TreeViewFile.readFileList();
                                    bFileRead = true;
                                });
                            }
                        } else if (receivedData.contains("... ")) {
                            String str = caREPL.getText();
                            iPosCaretBlock = str.lastIndexOf("... ") + 4;
                            iPosCommStrt = str.length();
                            moveCaretTo(iPosCommStrt);
                        }
                    });
                        
                }
                catch (SerialPortException ex) {
                    System.out.println("Error in receiving string from COM-port: " + ex);
                }
            }
        }
    }
    
    static public boolean isIdle() {
        return bIdleMode;
    }
    
    static public void setDisable(boolean bVal){
        caREPL.setDisable(bVal);
    }
    
    static public void appendText(String str) {
        caREPL.appendText(str+"\n");
    }
}
