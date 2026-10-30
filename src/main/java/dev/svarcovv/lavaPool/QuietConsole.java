package dev.svarcovv.lavaPool;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.UUID;

final class QuietConsole {
    private QuietConsole() {
    }

    static ConsoleCommandSender wrap(ConsoleCommandSender console) {
        CommandSender.Spigot silentSpigot = new CommandSender.Spigot() {
            @Override
            public void sendMessage(BaseComponent component) {
            }

            @Override
            public void sendMessage(BaseComponent... components) {
            }

            @Override
            public void sendMessage(UUID sender, BaseComponent component) {
            }

            @Override
            public void sendMessage(UUID sender, BaseComponent... components) {
            }
        };
        return (ConsoleCommandSender) Proxy.newProxyInstance(ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().startsWith("send") || method.getName().equals("showTitle")
                            || method.getName().equals("playSound")) {
                        return null;
                    }
                    if (method.getName().equals("spigot")) {
                        return silentSpigot;
                    }
                    try {
                        return method.invoke(console, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }
}
