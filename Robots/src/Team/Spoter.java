package team;

import robocode.*;
import java.awt.geom.Point2D;
import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Line2D;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import static robocode.util.Utils.normalRelativeAngleDegrees;

public class Spoter extends TeamRobot {

    private AdvancedEnemyBot enemy = new AdvancedEnemyBot();
    private HashMap<String, Mate> team = new HashMap<>();
    private byte radarDirection = 1;
    private byte moveDirection = 1;
    private int myNumber;
    private PAD_Space emotions = new PAD_Space();

    public void run() {
        myNumber = getBotNumber(this.getName());
        setColors(Color.white, Color.black, Color.magenta);
        setAdjustRadarForGunTurn(true);
        setAdjustGunForRobotTurn(true);
        Mate myself = new Mate(getName(), getX(), getY());
        try {
            broadcastMessage(myself);
        } catch (IOException ex) {
            Logger.getLogger(Spoter.class.getName()).log(Level.SEVERE, null, ex);
        }
        while (true) {
            myself = new Mate(getName(), getX(), getY());
            try {
                broadcastMessage(myself);
            } catch (IOException ex) {
                Logger.getLogger(Spoter.class.getName()).log(Level.SEVERE, null, ex);
            }
            doRadar();
            doMove();
            doGun();
            execute();
        }
    }

    public void onDeath(DeathEvent event) {
        try {
            broadcastMessage("DIED");
        } catch (IOException ex) {
            Logger.getLogger(Spoter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {

        if (!isTeammate(e.getName())) {
            if (enemy.none() || e.getDistance() < enemy.getDistance() - 70
                    || e.getName().equals(enemy.getName())) {

                enemy.update(e, this);

            } else {
                emotions.updateArousal(10);

                emotions.updateDominance(10);
                emotions.updatePleasure(10);
            }
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        // see if the robot we were tracking died
        if (e.getName().equals(enemy.getName())) {
            emotions.updateArousal(200);
            emotions.updateDominance(200);
            emotions.updatePleasure(200);
            enemy.reset();
        }
    }

    void doRadar() {
        if (enemy.none()) {
            // look around if we have no enemy
            setTurnRadarRight(360);
        } else {
            // oscillate the radar
            double turn = getHeading() - getRadarHeading() + enemy.getBearing();
            turn += 30 * radarDirection;
            setTurnRadarRight(normalizeBearing(turn));
            radarDirection *= -1;
        }
    }

    public void doMove() {

        // turn slightly toward our enemy
        setTurnRight(normalizeBearing(enemy.getBearing() + 90 - (15 * moveDirection)));

        // strafe toward him
        if (getTime() % 20 == 0) {
            moveDirection *= -1;
            setAhead(150 * moveDirection);
        }
    }

    void doGun() {

        if (enemy.none()) {
            return;
        }

        double firePower = Math.min(400 / enemy.getDistance(), 3);
        double bulletSpeed = 20 - firePower * 3;
        long time = (long) (enemy.getDistance() / bulletSpeed);
        double futureX = enemy.getFutureX(time);
        double futureY = enemy.getFutureY(time);
        double absDeg = absoluteBearing(getX(), getY(), futureX, futureY);
        setTurnGunRight(normalizeBearing(absDeg - getGunHeading()));
        Point3D spot = new Point3D(futureX, futureY, time);

        try {
            sendMessage("team.Shooter* (" + myNumber + ")", spot);
        } catch (IOException ex) {
            Logger.getLogger(Spoter.class.getName()).log(Level.SEVERE, null, ex);
        }

        //System.out.println(!in_line_of_fire(futureX,futureY));
        if (!in_line_of_fire(futureX, futureY, time)) {
            if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
                setFire(firePower);
            }
        }
    }

    // computes the absolute bearing between two points
    double absoluteBearing(double x1, double y1, double x2, double y2) {
        double xo = x2 - x1;
        double yo = y2 - y1;
        double hyp = Point2D.distance(x1, y1, x2, y2);
        double arcSin = Math.toDegrees(Math.asin(xo / hyp));
        double bearing = 0;

        if (xo > 0 && yo > 0) { // both pos: lower-Left
            bearing = arcSin;
        } else if (xo < 0 && yo > 0) { // x neg, y pos: lower-right
            bearing = 360 + arcSin; // arcsin is negative here, actually 360 - ang
        } else if (xo > 0 && yo < 0) { // x pos, y neg: upper-left
            bearing = 180 - arcSin;
        } else if (xo < 0 && yo < 0) { // both neg: upper-right
            bearing = 180 - arcSin; // arcsin is negative here, actually 180 + ang
        }

        return bearing;
    }

    // normalizes a bearing to between +180 and -180
    double normalizeBearing(double angle) {
        while (angle > 180) {
            angle -= 360;
        }
        while (angle < -180) {
            angle += 360;
        }
        return angle;
    }

    static public int getBotNumber(String name) {
        String n = "0";
        int low = name.indexOf("(") + 1;
        int hi = name.lastIndexOf(")");
        if (low >= 0 && hi >= 0) {
            n = name.substring(low, hi);
        }
        return Integer.parseInt(n);
    }

    public void onMessageReceived(MessageEvent e) {
        if (e.getMessage() instanceof Mate) {
            Mate mate = (Mate) e.getMessage();
            team.put(mate.getName(), mate);
        }
        if (e.getMessage() instanceof String) {
            team.remove(e.getSender());
        }
    }

   public boolean in_line_of_fire(double x, double y, long when) {
        for (Mate m : team.values()) {
            if (Line2D.linesIntersect(m.getX() - 50, m.getY() - 50, m.getX() + 50, m.getY() + 50, getFutureX(when), getFutureY(when), x, y)
                    || Line2D.linesIntersect(m.getX() + 50, m.getY() + 50, m.getX() - 50, m.getY() - 50, getFutureX(when), getFutureY(when), x, y)) {
                return true;
            }
        }
        return false;
    }

    public double getFutureX(long when) {
        /*
		double sin = Math.sin(Math.toRadians(getHeading()));
		double futureX = x + sin * getVelocity() * when;
		return futureX;
         */
        return getY() + Math.sin(Math.toRadians(getHeading())) * getVelocity() * when;
    }

    public double getFutureY(long when) {
        /*
		double cos = Math.cos(Math.toRadians(getHeading()));
		double futureY = y + cos * getVelocity() * when;
		return futureY;
         */
        return getX() + Math.cos(Math.toRadians(getHeading())) * getVelocity() * when;
    }

    public void onHitByBullet(HitByBulletEvent event) {
        int power = (int) event.getPower() * 2;
        emotions.updateArousal(-power);
        emotions.updateDominance(-power);
        emotions.updatePleasure(-power);
    }

    @Override
    public void onHitRobot(HitRobotEvent event) {
        emotions.updateArousal(-10);
        emotions.updateDominance(-10);
        emotions.updatePleasure(-10);
    }

    @Override
    public void onBulletHit(BulletHitEvent event) {
        int power = (int) event.getBullet().getPower();
        if (!isTeammate(event.getName())) {
            emotions.updateArousal(power);
            emotions.updateDominance(power);
            emotions.updatePleasure(power);
        } else {
            emotions.updateArousal(-power);
            emotions.updateDominance(-power);
            emotions.updatePleasure(-power);
        }
    }

    @Override
    public void onBulletMissed(BulletMissedEvent event) {
        int power = (int) event.getBullet().getPower() / 2;
        emotions.updateArousal(power);
        emotions.updateDominance(power);
        emotions.updatePleasure(power);
    }

    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        System.out.println(emotions.evaluate());
    }

    @Override
    public void onHitWall(HitWallEvent event) {
        emotions.updateArousal(-1);
        emotions.updateDominance(-1);
        emotions.updatePleasure(-1);
    }

}
