package frc.robot.commands;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Constants.SwerveConstants;
import frc.robot.Constants.VisionConstants;
import frc.robot.subsystems.SwerveSubsystem;
import frc.robot.subsystems.Limelight.LimelightHelpers;


// Positions Robot at the Nearest Valid Target
public class AimNRangeCommand extends Command {
    
  // Instantiate Stuff
  SwerveSubsystem m_swerveSubsystem;

  // Timer for cancellation with failed robot adjustment
  Timer timer = new Timer();

  // PID Controller stuff (woah so many so scary) 
  PIDController m_aimController = new PIDController(VisionConstants.kP_aim, VisionConstants.kI_aim, VisionConstants.kD_aim);
  PIDController m_rangeController = new PIDController(VisionConstants.kP_range, VisionConstants.kI_range, VisionConstants.kD_range);
  PIDController m_strafeController = new PIDController(VisionConstants.kP_strafe, VisionConstants.kI_strafe, VisionConstants.kD_strafe);

  // Bot Pose Target Space Relative [TX, TY, TZ, Pitch, Yaw, Roll]
  private double[] botPoseTargetSpace = new double[6];

  /*
   * Tag Guide (Perspective is from respective DS):
   * 1: Coral Station Red Left
   * 2: Coral Station Red Right
   * 3: Processor Blue
   * 4: Barge Blue Back
   * 5: Barge Red Front
   * 6: Reef Red Front Left
   * 7: Reef Red Front Center
   * 8: Reef Red Front Right
   * 9: Reef Red Back Right
   * 10: Reef Red Back Center
   * 11: Reef Red Back Left
   * 12: Coral Station Blue Right
   * 13: Coral Station Blue Left
   * 14: Barge Blue Front
   * 15: Barge Red Back
   * 16: Processor Red
   * 17: Reef Blue Front Right
   * 18: Reef Blue Front Center
   * 19: Reef Blue Front Left
   * 20: Reef Blue Back Left
   * 21: Reef Blue Back Center
   * 22: Reef Blue Back Right
   */

  // All the Valid IDs available for positioning
  int[] validIDs = {6, 7, 8, 9, 10, 11, 17, 18, 19, 20, 21, 22};

  // Lil boolean for checking for "Tag In View" 
  private boolean tiv;

  // Constants
  private double m_rangeTarget;
  private double m_strafeTarget;
  private double m_aimTarget;

  // Constructor
  public AimNRangeCommand(SwerveSubsystem driveSubsystem) {
        
    // Definitions and setting parameters are equal to members!
    m_swerveSubsystem = driveSubsystem;
    addRequirements(driveSubsystem);
  }

  // What we do to set up the command 
  public void initialize() {

    // Adds condition that filters out undesired IDs
    LimelightHelpers.SetFiducialIDFiltersOverride(VisionConstants.k_limelightName, validIDs);

    // Update BotPoseTargetSpace
    botPoseTargetSpace = NetworkTableInstance.getDefault().getTable(VisionConstants.k_limelightName).getEntry("botpose_targetspace").getDoubleArray(new double[6]);

    // Checks for TIV
    tiv = (LimelightHelpers.getTV(VisionConstants.k_limelightName) 
      && botPoseTargetSpace[2] > VisionConstants.k_tzValidRange 
      && Math.abs(botPoseTargetSpace[4]) < VisionConstants.k_yawValidRange
    );

    // Set Constants
    if (VisionConstants.k_isRightReef) {
      m_strafeTarget = VisionConstants.k_strafeReefRightTarget;
      m_rangeTarget = VisionConstants.k_rangeReefRightTarget;
      m_aimTarget = VisionConstants.k_aimReefRightTarget;
    }
    else {
      m_strafeTarget = VisionConstants.k_strafeReefLeftTarget;
      m_rangeTarget = VisionConstants.k_rangeReefLeftTarget;
      m_aimTarget = VisionConstants.k_aimReefLeftTarget;
    }

    // Timer Reset
    timer.start();
    timer.reset();
  }
    
  // The actual control!
  public void execute() {

    VisionConstants.k_positioning = true;

    // Update the pose from NetworkTables (Limelight Readings)
    botPoseTargetSpace = NetworkTableInstance.getDefault().getTable(VisionConstants.k_limelightName).getEntry("botpose_targetspace").getDoubleArray(new double[6]);

    if (tiv){
      tiv = LimelightHelpers.getTV(VisionConstants.k_limelightName)
        && botPoseTargetSpace[2] > VisionConstants.k_tzValidRange;
      m_swerveSubsystem.driveCommandLimelight(limelight_range_PID(), limelight_strafe_PID(), limelight_aim_PID());
    }

    // If tags are in view, drive right over!
    // if (LimelightHelpers.getTV(VisionConstants.k_limelightName)) m_swerveSubsystem.driveCommand(() -> limelight_strafe_PID(), () -> limelight_range_PID(), () -> limelight_aim_PID());

    // Otherwise we tell it to quit
    // else tiv = false;
  }

  // Add stuff we do after to reset here (a.k.a tag filters)
  public void end(boolean interrupted) {
    VisionConstants.k_positioning = false;
  }

  // Are we done yet? Finishes when threshold is reached or if no tag in view or if timer is reached 
  public boolean isFinished() {
    return (
      // Strafe (Right Right Positioning)
      Math.abs(botPoseTargetSpace[0] - m_strafeTarget)  < VisionConstants.k_strafeThreshold) &&
      // Range (Distance to Tag)
      Math.abs(botPoseTargetSpace[2] - m_rangeTarget) < VisionConstants.k_rangeThreshold &&
      // Aim (Angle)
      Math.abs(botPoseTargetSpace[4] - m_aimTarget)  < VisionConstants.k_aimThreshold

      // Other quit conditions
      || !tiv || timer.get() > 3;
  }

   // Advanced PID-assisted ranging control with Limelight's TX value from target-relative data
   private double limelight_strafe_PID() {

    // Limelight X Axis Range in Meters
    m_strafeController.enableContinuousInput(-2, 2); 
    
    // Calculates response based on difference in horizontal distance from tag to robot
    double targetingStrafeSpeed = m_strafeController.calculate(botPoseTargetSpace[0] - m_strafeTarget);

    // Value scale up to robot max speed (Double can't exceed 1.0)
    targetingStrafeSpeed *= -1.0 * SwerveConstants.k_maxSpeed;

    // Hooray
    return targetingStrafeSpeed;
  }

  // Advanced PID-assisted ranging control with Limelight's TZ value from target-relative data
  private double limelight_range_PID() {

    // Limelight Z Axis Range in meters
    m_rangeController.enableContinuousInput(-2, 0);
    
    // Calculates response based on difference in distance from tag to robot
    double targetingForwardSpeed = m_rangeController.calculate(botPoseTargetSpace[2] - m_rangeTarget);

    // Value scale up to robot max speed and invert (double cannot exceed 1.0)
    targetingForwardSpeed *= 1.0 * SwerveConstants.k_maxSpeed;

    // Hooray
    return targetingForwardSpeed;
  }

  // Advanced PID-assisted ranging control with Limelight's Yaw value from target-relative data
  private double limelight_aim_PID() {

    // Limelight Yaw Angle in Degrees
    m_aimController.enableContinuousInput(-30, 30);
    
    // Calculates response based on difference in angle from tag to robot
    double targetingAngularVelocity = m_aimController.calculate(botPoseTargetSpace[4] - m_aimTarget);

    // Multiply by -1 because robot is CCW Positive. Multiply by a reduction 
    // multiplier to reduce speed. Scale TX up with robot speed.
    targetingAngularVelocity *= -0.1 * SwerveConstants.k_maxAngularSpeed;

    // Hooray
    return targetingAngularVelocity;
  }
}
