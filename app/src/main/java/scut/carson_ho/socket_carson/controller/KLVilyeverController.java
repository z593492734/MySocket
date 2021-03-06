package scut.carson_ho.socket_carson.controller;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.vilyever.socketclient.SocketClient;
import com.vilyever.socketclient.helper.SocketStateChangeCallback;
import com.vilyever.socketclient.helper.PackageReciveCallback;
import com.vilyever.socketclient.helper.PackageSendCallback;
import com.vilyever.socketclient.helper.SocketHeartBeatHelper;
import com.vilyever.socketclient.helper.SocketPacket;
import com.vilyever.socketclient.helper.SocketPacketHelper;
import com.vilyever.socketclient.helper.SocketResponsePacket;
import com.vilyever.socketclient.util.CharsetUtil;

import java.util.Arrays;

import scut.carson_ho.socket_carson.KLSocketBean;
import scut.carson_ho.socket_carson.OperationType;
import scut.carson_ho.socket_carson.SocketConfig;

/**
 * Author：mengyuan
 * Date  : 2017/6/5上午11:07
 * E-Mail:mengyuanzz@126.com
 * Desc  :
 */

public class KLVilyeverController {
    private static KLVilyeverController ourInstance = new KLVilyeverController();

    private SocketClient localSocketClient;

    private int heartTag;
    private int loginTag;


    private boolean isLogin = false;

    private String ip;
    private int port;
    //从Activity传来的Handler
    private Handler mainHandler;

    public static KLVilyeverController getInstance() {
        if (ourInstance == null) {
            synchronized (new Object()) {
                if (ourInstance == null) {
                    ourInstance = new KLVilyeverController();
                }
            }
        }
        return ourInstance;
    }

    private KLVilyeverController() {
    }

    /**
     * PUBLIC
     * 连接服务器
     *
     * @param ip
     * @param port
     */
    public synchronized void connectionSocket(final String ip, final int port, final Handler mainHandler) {
        //如果服务器已经连接
        if (localSocketClient != null && localSocketClient.isConnected()) {
            localSocketClient.disconnect();
            localSocketClient = null;
        }
        if (localSocketClient == null) {
            localSocketClient = new SocketClient();
        }
        isLogin = false;
        this.mainHandler = mainHandler;
        this.ip = ip;
        this.port = port;
        //设置远端地址
        setSocketAddress(ip, port + "");
        //设置编码格式
        localSocketClient.setCharsetName(CharsetUtil.UTF_8); // 设置编码为UTF-8
        //设置动态的心跳包内容
        setAutoHeartContent();
        //在发送每个数据包时，发送每段数据的最长时间，超过后自动断开socket连接
        localSocketClient.getSocketPacketHelper().setSendTimeout(SocketConfig.HEART_SEND_TIME);
        // 设置允许使用发送超时时长，此值默认为false
        localSocketClient.getSocketPacketHelper().setSendTimeoutEnabled(true);

        //注册连接状态改变监听
        localSocketClient.registerSocketStateChangeCallback(new SocketStateChangeCallback() {
            /**
             * 连接上远程端时的回调
             */
            @Override
            public void onConnected(SocketClient client) {
                Log.i("mengyuansocket", "成功连接上服务器：" + client.getAddress().toString());
                //去登录
                login();
            }

            /**
             * 与远程端断开连接时的回调
             */
            @Override
            public void onDisconnect(final SocketClient client) {

                Log.i("mengyuansocket", "与服务器断开了连接，3秒后重新连接");
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        try {
                            Thread.sleep(3 * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        connectionSocket(ip, port, mainHandler);

                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);

                    }
                }.execute();
            }

            @Override
            public void onDisconnected(SocketClient client) {
                Log.i("mengyuansocket", "手动断开了连接");
            }
        });

        /**
         * 注册数据发送监听
         */
        localSocketClient.registerPackageSendCallback(new PackageSendCallback() {
            /**
             * 数据包开始发送时的回调
             */
            @Override
            public void onSendPacketBegin(SocketClient client, SocketPacket packet) {
                Log.i("mengyuansocket", "数据包开始发送时的回调: " + packet.hashCode() + "   " + Arrays.toString(packet.getData()));
            }

            /**
             * 数据包取消发送时的回调
             * 取消发送回调有以下情况：
             * 1. 手动cancel仍在排队，还未发送过的packet
             * 2. 断开连接时，正在发送的packet和所有在排队的packet都会被取消
             */
            @Override
            public void onSendPacketCancel(SocketClient client, SocketPacket packet) {
                Log.i("mengyuansocket", "数据包取消发送时的回调: " + packet.hashCode());
            }


            /**
             * 数据包完成发送时的回调
             */
            @Override
            public void onSendPacketEnd(SocketClient client, SocketPacket packet) {
                Log.i("mengyuansocket", "数据包完成发送时的回调: " + packet.hashCode());
            }
        });

        /**
         * 注册数据接收的监听
         */
        localSocketClient.registerPackageReceiveCallback(new PackageReciveCallback() {
            @Override
            public void onReceivePacketBegin(SocketClient client, SocketResponsePacket packet) {
                Log.i("mengyuansocket", "接收数据包开始: " + packet.hashCode());
            }

            @Override
            public void onReceivePacketEnd(SocketClient client, SocketResponsePacket responsePacket) {
                Log.i("mengyuansocket", "onResponse: " + responsePacket.hashCode() + " 【" + responsePacket.getMessage() + "】 " + " isHeartBeat: " + responsePacket.isHeartBeat() + " " + Arrays.toString(responsePacket.getData()));
                if (responsePacket.getData() == null) {
                    return;
                }
                KLSocketBean bean = KLSocketBean.toSocketBean(responsePacket.getData());
                if (bean == null) {
                    return;
                }
                switch (bean.operationType) {
                    case OperationType.TYPE_LOGIN_SERVER://登录成功
                        Log.i("mengyuansocket", "登录成功");
                        localSocketClient.getHeartBeatHelper().setSendHeartBeatEnabled(true); // 设置允许自动发送心跳包，此值默认为false
//                            localSocketClient.getSocketConfigure().getHeartBeatHelper().setSendHeartBeatEnabled(isLogin = true); // 设置允许自动发送心跳包，此值默认为false
                        break;
                    case OperationType.TYPE_HEART_SERVER://心跳成功
                        Log.i("mengyuansocket", "心跳成功");
                        break;
                }
            }

            @Override
            public void onReceivePacketCancel(SocketClient client, SocketResponsePacket packet) {
                Log.i("mengyuansocket", "接收数据包已取消: " + packet.hashCode());
            }


        });


        localSocketClient.connect();
    }


    public void login() {
        if (localSocketClient == null) {
            return;
        }
        //发送登录请求
        localSocketClient.sendData(KLSocketBean.createAppLoginPackage("zhongjin", "test", ++loginTag));

    }

    public void stop() {
        if (localSocketClient == null) {
            return;
        }
        localSocketClient.disconnect();
        localSocketClient = null;
    }

    public void sendHeart() {
        if (localSocketClient == null) {
            return;
        }
        //发送心跳包请求
        localSocketClient.sendData(KLSocketBean.createAppHeartPackage(++heartTag));
    }

    /**
     * 设置远程端地址信息
     */
    private void setSocketAddress(String ip, String port) {
        localSocketClient.getAddress().setRemoteIP(ip); // 远程端IP地址
        localSocketClient.getAddress().setRemotePort(port); // 远程端端口号
        localSocketClient.getAddress().setConnectionTimeout(SocketConfig.CONNECTION_TIME); // 连接超时时长，单位毫秒
    }



    /**
     * 设置自动生成的心跳包内容
     * 这里的例子是使用不同的日期
     */
    private void setAutoHeartContent() {
        /**
         * 设置自动发送的心跳包信息
         * 此信息动态生成
         *
         * 每次发送心跳包时自动调用
         */
        localSocketClient.getHeartBeatHelper().setSendDataBuilder(new SocketHeartBeatHelper.SendDataBuilder() {
            @Override
            public byte[] obtainSendHeartBeatData(SocketHeartBeatHelper helper) {

                return KLSocketBean.createAppHeartPackage(++heartTag);
            }
        });

        /**
         * 设置远程端发送到本地的心跳包信息的检测器，用于判断接收到的数据包是否是心跳包
         * 通过{@link SocketResponsePacket#isHeartBeat()} 查看数据包是否是心跳包
         */
        localSocketClient.getHeartBeatHelper().setReceiveHeartBeatPacketChecker(new SocketHeartBeatHelper.ReceiveHeartBeatPacketChecker() {
            @Override
            public boolean isReceiveHeartBeatPacket(SocketHeartBeatHelper helper, SocketResponsePacket responsePacket) {
                /**
                 * 判断数据包信息是否含有指定的心跳包前缀和后缀
                 */
                Log.i("mengyuansocket", "isReceiveHeartBeatPacket返回的数据: " + responsePacket.hashCode() + " 【" + responsePacket.getMessage() + "】 " + " isHeartBeat: " + responsePacket.isHeartBeat() + " " + Arrays.toString(responsePacket.getData()));

                if (responsePacket.getData() == null) {
                    Log.i("mengyuansocket", "isReceiveHeartBeatPacket-数据为空");
                    return false;
                }
                KLSocketBean bean = KLSocketBean.toSocketBean(responsePacket.getData());
                if (bean == null) {
                    Log.i("mengyuansocket", "isReceiveHeartBeatPacket-解析失败");
                    return false;
                }

                if (bean.operationType == OperationType.TYPE_HEART_SERVER) {
                    Log.i("mengyuansocket", "isReceiveHeartBeatPacket-为心跳包");
                    return true;
                }
                Log.i("mengyuansocket", "isReceiveHeartBeatPacket-不为为心跳包");

                return false;
            }
        });

        localSocketClient.getHeartBeatHelper().setHeartBeatInterval(SocketConfig.HEART_SEND_TIME); // 设置自动发送心跳包的间隔时长，单位毫秒
        localSocketClient.getHeartBeatHelper().setSendHeartBeatEnabled(false); // 设置允许自动发送心跳包，此值默认为false
    }


}
