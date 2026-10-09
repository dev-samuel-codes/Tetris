package backend.network;

import java.io.IOException;

// 예전에 쓰던 연결 확인 실행 명령도 점수 서버로 이어지도록 남겨 둔 진입점
public final class ConnectionTestServer {
    private ConnectionTestServer() {
    }

    // PING 확인은 그대로 지원하고 두 번째 인자로 점수 저장 폴더도 지정할 수 있음
    public static void main(String[] args) throws IOException {
        ScoreServer.main(args);
    }
}
