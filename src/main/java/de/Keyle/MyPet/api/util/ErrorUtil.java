/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2025 Keyle
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

package de.Keyle.MyPet.api.util;

import de.Keyle.MyPet.MyPetApi;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 用于集中记录错误日志的工具类。
 * 将错误和堆栈跟踪记录到插件控制台。
 */
public class ErrorUtil {

    private ErrorUtil() {
        // 工具类 —— 禁止实例化
    }

    /**
     * 以 SEVERE 级别报告错误。
     * 将消息和完整堆栈跟踪记录到控制台。
     *
     * @param e 要报告的异常
     */
    public static void report(Throwable e) {
        report(e.getMessage(), e);
    }

    /**
     * 以 SEVERE 级别和自定义消息报告错误。
     * 将消息和完整堆栈跟踪记录到控制台。
     *
     * @param message 自定义错误消息
     * @param e 要报告的异常
     */
    public static void report(String message, Throwable e) {
        reportSevere(message, e);
    }

    /**
     * 报告警告级别的错误。
     * 将消息和完整堆栈跟踪记录到控制台。
     *
     * @param e 要报告的异常
     */
    public static void reportWarning(Throwable e) {
        reportWarning(e.getMessage(), e);
    }

    /**
     * 以自定义消息报告警告级别的错误。
     * 将消息和完整堆栈跟踪记录到控制台。
     *
     * @param message 自定义警告消息
     * @param e 要报告的异常
     */
    public static void reportWarning(String message, Throwable e) {
        logError(Level.WARNING, message, e);
    }

    /**
     * 报告错误级别的错误。
     * 将消息和完整堆栈跟踪记录到控制台。
     *
     * @param e 要报告的异常
     */
    public static void reportError(Throwable e) {
        reportError(e.getMessage(), e);
    }

    /**
     * 以自定义消息报告错误级别的错误。
     * 将消息和完整堆栈跟踪记录到控制台。
     *
     * @param message 自定义错误消息
     * @param e 要报告的异常
     */
    public static void reportError(String message, Throwable e) {
        logError(Level.SEVERE, message, e);
    }

    /**
     * 报告严重级别的错误。
     * 将消息和完整堆栈跟踪记录到控制台。
     *
     * @param e 要报告的异常
     */
    public static void reportSevere(Throwable e) {
        reportSevere(e.getMessage(), e);
    }

    /**
     * 以自定义消息报告严重级别的错误。
     * 将消息和完整堆栈跟踪记录到控制台。
     *
     * @param message 自定义错误消息
     * @param e 要报告的异常
     */
    public static void reportSevere(String message, Throwable e) {
        logError(Level.SEVERE, message, e);
    }

    /**
     * 将错误及其堆栈跟踪记录到控制台。
     *
     * @param level 日志级别
     * @param message 错误消息
     * @param e 异常
     */
    private static void logError(Level level, String message, Throwable e) {
        Logger logger = MyPetApi.getLogger();

        logger.log(level, "✘ MyPet has encountered an error!");
        // 连同完整堆栈跟踪一起输出到控制台
        if (message != null && !message.isEmpty()) {
            logger.log(level, message, e);
        } else {
            logger.log(level, "An error occurred", e);
        }

    }
}
