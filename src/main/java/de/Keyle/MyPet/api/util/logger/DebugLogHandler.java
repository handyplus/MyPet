/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2020 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.api.util.logger;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.Configuration;
import de.Keyle.MyPet.api.util.LogFormat;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 管理 MyPet 的调试文件日志。
 * 为插件日志器添加一个 FileHandler，用于写入 plugins/MyPet/logs/MyPet.log
 */
public class DebugLogHandler {

    private static final String HANDLER_NAME = "MyPet-Debug-Logger-FileHandler";
    private static FileHandler debugLogFileHandler = null;

    /**
     * 在给定的日志器上设置调试文件处理器。
     * 使用 Configuration.Log.LEVEL 中的日志级别。
     *
     * @param logger 要添加处理器的日志器
     * @return 处理器是否设置成功
     */
    public static boolean setup(Logger logger) {
        if (logger == null) {
            return false;
        }

        // 检查处理器是否已存在
        for (Handler h : logger.getHandlers()) {
            if (HANDLER_NAME.equals(h.toString())) {
                if (Configuration.Log.LEVEL.equalsIgnoreCase("OFF")) {
                    logger.removeHandler(h);
                    h.close();
                    debugLogFileHandler = null;
                    return false;
                }
                debugLogFileHandler = (FileHandler) h;
                return true;
            }
        }

        if (Configuration.Log.LEVEL.equalsIgnoreCase("OFF")) {
            return false;
        }

        // 如果可用则复用已有的处理器
        if (debugLogFileHandler != null) {
            logger.addHandler(debugLogFileHandler);
            return true;
        }

        // 创建新的处理器
        try {
            File logsFolder = new File(MyPetApi.getPlugin().getDataFolder(), "logs");
            logsFolder.mkdirs();
            File logFile = new File(logsFolder, "MyPet.log");

            FileHandler fileHandler = new FileHandler(logFile.getAbsolutePath(), true) {
                @Override
                public String toString() {
                    return HANDLER_NAME;
                }
            };

            Level level = parseLevel(Configuration.Log.LEVEL);
            fileHandler.setLevel(level);
            fileHandler.setFormatter(new LogFormat());
            logger.addHandler(fileHandler);
            debugLogFileHandler = fileHandler;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 更新调试处理器的日志级别。
     */
    public static void updateLogLevel() {
        if (debugLogFileHandler != null) {
            Level level = parseLevel(Configuration.Log.LEVEL);
            debugLogFileHandler.setLevel(level);
        }
    }

    /**
     * 禁用并从给定的日志器中移除调试处理器。
     *
     * @param logger 要移除处理器的日志器
     */
    public static void disable(Logger logger) {
        if (logger == null) {
            return;
        }
        for (Handler h : logger.getHandlers()) {
            if (HANDLER_NAME.equals(h.toString())) {
                logger.removeHandler(h);
                h.close();
            }
        }
        debugLogFileHandler = null;
    }

    private static Level parseLevel(String levelName) {
        try {
            return Level.parse(levelName);
        } catch (IllegalArgumentException e) {
            return Level.OFF;
        }
    }
}