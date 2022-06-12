package com.mrc.herd;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.Timer;


public class TitlePanel {
  Timer title_timer;
  BufferedImage bi;
  Graphics2D g;
  BufferedImage target;
  Font dmFont,crFont;
  BufferedImage[] icons = new BufferedImage[7];
  Graphics2D transfer;
  int wid;
  int hei;
  static String healthy = "Healthy";
  static String vacc = "Vaccinated";
  static String infun = "Sick & Infectious";
  static String immune = "Recovered";
  static String vacc_cov = "Vaccination Coverage: ";
  static String r0_string = "Avg New Infections (R0): ";
  static String job_name_Text = "Name:";
  Herd h;
  public boolean enable_buttons;

  public void startAnimation(int _wid, int _hei,Graphics2D _target) {
    enable_buttons=true;
    transfer=_target;
    g.setColor(Color.BLACK);
    wid=_wid;
    hei=_hei;
    g.fillRect(0, 0, wid,hei);
    interactiveMenu();
  }

  public void interactiveMenu() {
    g.setColor(Color.WHITE);
    g.setFont(dmFont);
    g.drawImage(icons[0],(wid/2)-200,100,null);
    g.drawString(healthy, (wid/2)-100,138);
    g.drawImage(icons[1],(wid/2)-200,190,null);
    g.drawString(vacc, (wid/2)-100,228);
    g.drawImage(icons[2],(wid/2)-200,280,null);
    g.drawString(infun, (wid/2)-100,318);
    g.drawImage(icons[3],(wid/2)-200,370,null);
    g.drawString(immune, (wid/2)-100,408);
    g.drawString(vacc_cov,(wid/2)-250,508);
    g.drawString(r0_string,(wid/2)-296,558);
    if (!h.job_name.trim().equals("")) g.drawString(job_name_Text,(wid/2)+30,608);
    g.setFont(crFont);
    g.setColor(Color.RED);
    String v = String.valueOf(h.vacc_coverage);
    g.drawString(v,(wid/2)+220,508);
    g.drawString("%",(wid/2)+270,508);
    if (h.gui_mode==Herd.PC) {
      g.drawImage(icons[4],(wid/2)+165,480,null);
      g.drawImage(icons[5],(wid/2)+315,480,null);
      g.drawImage(icons[6],(wid/2)-28,580,null);
      g.drawImage(icons[4],(wid/2)+165,530,null);
      g.drawImage(icons[5],(wid/2)+315,530,null);
    }
    v = String.valueOf(h.r0);
    g.drawString(v,(wid/2)+245,558);
    if (!h.job_name.trim().equals("")) g.drawString(String.valueOf(h.job_name),(wid/2)+245,608);
    transfer.drawImage(bi, 0, 0, null);
    enable_buttons=true;
  }

  public void clickButton(int x, int y) {
    boolean edit_cov=false;
    boolean edit_r0=false;
    if ((y>=480) && (y<=512) && (x>=(wid/2)+165) && (x<=(wid/2)+197)) {
      if (h.vacc_coverage>0) {
        h.vacc_coverage-=10;
        edit_cov=true;
      }
    }
    else if ((y>=480) && (y<=512) && (x>=(wid/2)+315) && (x<=(wid/2)+347)) {
      if (h.vacc_coverage<90) {
        h.vacc_coverage+=10;
        edit_cov=true;
      }
    }

    if ((y>=530) && (y<=562) && (x>=(wid/2)+165) && (x<=(wid/2)+197)) {
      if (h.r0>3) {
        h.r0--;
        edit_r0=true;
      }
    }
    else if ((y>=530) && (y<=562) && (x>=(wid/2)+315) && (x<=(wid/2)+347)) {
      if (h.r0<8) {
        h.r0++;
        edit_r0=true;
      }
    }



    else if ((y>=580) && (y<=674) && (x>=(wid/2)-28) && (x<=(wid/2)+28)) {
      enable_buttons=false;
      h.current_mode=Herd.ENDING_TITLE;
      h.frame_timer.start();
    }
    if (edit_cov) {
      g.setColor(Color.BLACK);
      g.fillRect((wid/2)+220,480,60,30);
      g.setFont(crFont);
      g.setColor(Color.RED);
      String v = String.valueOf(h.vacc_coverage);
      g.drawString(v,(wid/2)+220,508);
      g.drawString("%",(wid/2)+270,508);

    }

    if (edit_r0) {
      g.setColor(Color.BLACK);
      g.fillRect((wid/2)+220,530,60,30);
      g.setFont(crFont);
      g.setColor(Color.RED);
      String v = String.valueOf(h.r0);
      g.drawString(v,(wid/2)+245,558);

    }

    if ((edit_r0) || (edit_cov)) {
      transfer.drawImage(bi, 0, 0, null);
    }
  }

  public void titleFade(float opac) {
    transfer.drawImage(bi,0,0,null);
    if (opac<1.0f) {
      Color c = new Color(0,0,0,1.0f-opac);
      g.setColor(c);
      g.fillRect(0, 0, wid,hei);
    }
  }

  public TitlePanel(int wid, int hei,Herd herd) {
    h=herd;
    try {
      bi = new BufferedImage(wid,hei,BufferedImage.TYPE_3BYTE_BGR);
      g = (Graphics2D) bi.getGraphics();
      // Legend = 4 rows of icon, and some text next to it.

      icons[0]=ImageIO.read(new File("images/big_sus.png"));
      icons[1]=ImageIO.read(new File("images/big_vac.png"));
      icons[2]=ImageIO.read(new File("images/big_inf.png"));
      icons[3]=ImageIO.read(new File("images/big_imm.png"));
      icons[4]=ImageIO.read(new File("images/down.png"));
      icons[5]=ImageIO.read(new File("images/up.png"));
      icons[6]=ImageIO.read(new File("images/go.png"));
      g.setColor(Color.WHITE);
      dmFont = new Font("Dot Matrix",Font.PLAIN,36);
      crFont = new Font("Consolas",Font.BOLD,36);
      g.setFont(dmFont);

    } catch (Exception e) { e.printStackTrace(); }

  }

}
