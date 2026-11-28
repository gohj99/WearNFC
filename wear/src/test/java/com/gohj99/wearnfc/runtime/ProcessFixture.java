package com.gohj99.wearnfc.runtime;

public final class ProcessFixture {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("hang")) {
            Thread.sleep(60_000);
        } else {
            // 大于进程管道容量，先读完 stdout 再读 stderr 会死锁。
            byte[] data = new byte[256 * 1024];
            java.util.Arrays.fill(data, (byte) 'x');
            System.err.write(data);
            System.err.flush();
            System.out.write(data);
            System.out.flush();
            System.exit(7);
        }
    }
}
