/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2019 Keyle
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

package de.Keyle.MyPet.api.util.animation.particle;

import de.Keyle.MyPet.api.util.animation.ParticleAnimation;
import de.Keyle.MyPet.api.util.location.LocationHolder;
import org.bukkit.Location;

public abstract class OrbitAnimation extends ParticleAnimation {
    protected final double radius;
    protected final double stepAngle;
    protected int rotationAngle = 0;

    public OrbitAnimation(double radius, LocationHolder location) {
        super(9000, location);
        this.setFramesPerTick(1);
        this.radius = radius;
        this.stepAngle = 0.08;
        this.tickRate = 3;
    }

    @Override
    public void reset() {
        super.reset();
        rotationAngle = 0;
    }

    @Override
    public void tick(int frame, Location location) {
        Location loc = location.clone();

        //double xangle = Math.toRadians(i++); // 注意这里必须转换为弧度。
        //double xAxisCos = Math.cos(xangle); // 获取俯仰角（pitch）的 cos 值。
        //double xAxisSin = Math.sin(xangle); // 获取俯仰角（pitch）的 sin 值。

        //double yangle = Math.toRadians(i++); // 注意这里必须转换为弧度。
        //double yAxisCos = Math.cos(-yangle); // 获取偏航角（yaw）的 cos 值。
        //double yAxisSin = Math.sin(-yangle); // 获取偏航角（yaw）的 sin 值。

        double zangle = Math.toRadians(rotationAngle++); // 注意这里必须转换为弧度。
        double zAxisCos = Math.cos(zangle); // 获取翻滚角（roll）的 cos 值。
        double zAxisSin = Math.sin(zangle); // 获取翻滚角（roll）的 sin 值。

        Location rotLoc = new Location(loc.getWorld(), radius * Math.cos(frame * stepAngle), 0, radius * Math.sin(frame * stepAngle));

        //rotateAroundAxisX(rotLoc, xAxisCos, xAxisSin);
        //rotateAroundAxisY(rotLoc, yAxisCos, yAxisSin);
        rotateAroundAxisZ(rotLoc, zAxisCos, zAxisSin);

        loc.add(rotLoc);

        playParticleEffect(loc);
    }

    private void rotateAroundAxisX(Location v, double cos, double sin) {
        double y = v.getY() * cos - v.getZ() * sin;
        double z = v.getY() * sin + v.getZ() * cos;
        v.setY(y);
        v.setZ(z);
    }

    private void rotateAroundAxisY(Location v, double cos, double sin) {
        double x = v.getX() * cos + v.getZ() * sin;
        double z = v.getX() * -sin + v.getZ() * cos;
        v.setX(x);
        v.setZ(z);
    }

    private void rotateAroundAxisZ(Location v, double cos, double sin) {
        double x = v.getX() * cos - v.getY() * sin;
        double y = v.getX() * sin + v.getY() * cos;
        v.setX(x);
        v.setY(y);
    }
}