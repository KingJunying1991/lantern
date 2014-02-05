package org.lantern.proxy.pt;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.lantern.LanternUtils;
import org.littleshoot.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FTE implements PluggableTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(FTE.class);
    private static final String FTE_PATH_KEY = "path";

    private Executor client;
    private Executor server;
    private Properties props;

    public FTE(Properties props) {
        super();
        this.props = props;
    }

    @Override
    public InetSocketAddress startClient(
            InetSocketAddress getModeAddress,
            InetSocketAddress proxyAddress) {
        LOGGER.debug("Starting FTE client");
        InetSocketAddress address = new InetSocketAddress(
                getModeAddress.getAddress(),
                LanternUtils.findFreePort());

        client = fteProxy(
                "--mode", "client",
                "--client_port", address.getPort(),
                "--server_ip", proxyAddress.getAddress().getHostAddress(),
                "--server_port", proxyAddress.getPort());

        if (!LanternUtils.waitForServer(address, 5000)) {
            throw new RuntimeException("Unable to start FTE client");
        }

        return address;
    }

    @Override
    public void stopClient() {
        client.getWatchdog().destroyProcess();
    }

    @Override
    public void startServer(int port, InetSocketAddress giveModeAddress) {
        LOGGER.debug("Starting FTE server");

        try {
            String ip = NetworkUtils.getLocalHost().getHostAddress();

            server = fteProxy(
                    "--mode", "server",
                    "--server_ip", ip,
                    "--server_port", port,
                    "--proxy_ip",
                    giveModeAddress.getAddress().getHostAddress(),
                    "--proxy_port", giveModeAddress.getPort());
        } catch (UnknownHostException uhe) {
            throw new RuntimeException("Unable to determine interface ip: "
                    + uhe.getMessage(), uhe);
        }
        if (!LanternUtils.waitForServer(port, 5000)) {
            throw new RuntimeException("Unable to start FTE server");
        }
    }

    @Override
    public void stopServer() {
        server.getWatchdog().destroyProcess();
    }

    private Executor fteProxy(Object... args) {
        Executor cmdExec = new DefaultExecutor();
        cmdExec.setStreamHandler(new PumpStreamHandler(System.out, System.err,
                System.in));
        cmdExec.setProcessDestroyer(new ShutdownHookProcessDestroyer());
        cmdExec.setWatchdog(new ExecuteWatchdog(
                ExecuteWatchdog.INFINITE_TIMEOUT));
        String fteProxyLocation = String.format("%1$s/bin/fteproxy",
                getProp(FTE_PATH_KEY, true));
        CommandLine cmd = new CommandLine(fteProxyLocation);
        for (Object arg : args) {
            cmd.addArgument(String.format("\"%1$s\"", arg));
        }
        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
        try {
            cmdExec.execute(cmd, resultHandler);
            return cmdExec;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String getProp(String key, boolean required) {
        String prop = props.getProperty(key);
        if (required && prop == null) {
            throw new RuntimeException(String.format("Missing %1$s in props",
                    key));
        }
        return prop;
    }
}
