package com.mrc.herd;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Base64;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class Herd {
  String data_path = "";
  boolean pc_waiting_for_next_game=false;

  byte gui_mode;                      // Type of user interface...
  final static byte ANDROID = 1;      // Will poll a web address for commands for simulations to run
  final static byte PC = 2;           // Will expect mouse input from a PC
  final static byte UNATTENDED = 3;   // Will loop through demos non-interactively
  final static byte ANDROID_IDLE = 4; // Will loop through demos, but check online for new commands too.

  int f=0;
  String job_name="";               // Name of currently running job
  byte current_mode=TITLE_PAGE;     // Current operation.

  final static byte PLAYING=1;         // Playing a simulation
  final static byte ENDING_GAME=2;     // Fades and graphs
  final static byte TITLE_PAGE=3;      // Showing title page
  final static byte ENDING_TITLE=4;    // Fading out title page
  final static byte STARTING_GAME=5;   // Beginning the game
  final static byte BETWEEN_GAMES=6;   // Showing the graph - wondering what to do next.

  static String STATUS_DEMO = new String("DEMO");
  static String STATUS_RUNNING = new String("RUN");
  static String STATUS_WAITING = new String("WAIT");

  byte[] vacc_order = new byte[] {0,40,80,10,50,90,20,60,30,70};
  byte[] r0_order = new byte[] {5,8,4,7,3,6};
  byte current_vacc_order=0;
  byte current_r0_order=0;
  Font littleStat,bigStat;
  Font graphNum,graphTitle,graphBigTitle;
  String commandURL;
  // The Window

  JFrame main;
  JPanel game;
  TitlePanel title;
  Timer frame_timer,unattended_timer;
  EventHandler eh;
  Graphics2D g2d;
  int screen_width,screen_height;
  int sick_pos,recover_pos,healthy_pos,vacc_pos;

  final int START_WIGGLE_1 = 101;
  final int END_WIGGLE_1 = START_WIGGLE_1+50;
  final int START_WIGGLE_2 = END_WIGGLE_1+20;
  final int END_WIGGLE_2 = START_WIGGLE_2+50;
  final int START_BARS = END_WIGGLE_2+20;
  final int END_BARS = START_BARS+120;
  final int FADE_GRAPH = END_BARS+50;
  final int END_FADE_GRAPH = FADE_GRAPH+30;

  public void checkReset() {
    String backups = new String("");
    File ff = new File(".");
    File[] fs = ff.listFiles();
    for (int i=0; i<fs.length; i++) {
      if (fs[i].isDirectory()) {
        String n = fs[i].getName();
        if (n.startsWith("bkp_")) {
          if (backups.length()==0) backups+=base64_encode(fs[i].getName());
          else backups+=","+base64_encode(fs[i].getName());
        }
      }
    }
    if ((gui_mode==ANDROID) || (gui_mode==ANDROID_IDLE)) {
      try {
        URL url = new URL(commandURL+"?cmd=reset&bkps="+backups);
        InputStream is = url.openStream();  // throws an IOException
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String s = br.readLine();
        br.close();
        if (s.startsWith("RESTORE_")) {
          s=base64_decode(s.substring(8));
          Runtime.getRuntime().exec("restore.bat "+s);
        } else if (s.startsWith("CLEAR")) {
          Runtime.getRuntime().exec("clear.bat");
        }
      } catch (Exception e) { e.printStackTrace(); }
    }
  }


  BufferedImage scatter,scatter2;
  BufferedImage bar,bar2;
  BufferedImage comp,comp2;
  int scatter_width,scatter_height,scatter_marg;
  Graphics2D scatterG,scatterG2;
  Graphics2D barG,barG2,compG,compG2;
  double graph_margin=60;

  public void nextRun() {
    current_vacc_order++;
    if (current_vacc_order>=vacc_order.length) {
      current_vacc_order=0;
      current_r0_order++;
      if (current_r0_order>=r0_order.length){
        current_r0_order=0;
      }
    }
    vacc_coverage=vacc_order[current_vacc_order];
    r0=r0_order[current_r0_order];
    try {
      PrintWriter PW = new PrintWriter(new File("herd.ini"));
      PW.println(current_r0_order+"\t"+current_vacc_order);
      PW.close();
    } catch (Exception e) { e.printStackTrace(); }

  }

  String base64_encode(String string) {
    try {
		return Base64.getEncoder().encodeToString(string.getBytes());
    } catch (Exception e) { e.printStackTrace(); return null; }
  }

  String base64_decode(String string) {
    return new String(Base64.getDecoder().decode(string));
  }


  public static int poisson(double lambda) {
    double L = Math.exp(-lambda);
    double p = 1.0;
    int k = 0;
    do {
      k++;
      p *= Math.random();
    } while (p > L);
    return k - 1;
  }

  // The Game

  float[] person_x;
  float[] person_y;
  float[] person_velx;
  float[] person_vely;
  byte[] person_status;
  byte[] person_contacts;
  int[] person_infector;
  int[][] person_dups;
  int[] person_infs;
  int no_infs,no_recoveries,no_healthy,no_vaccinations;
  int prev_no_infs,prev_no_recoveries,prev_no_healthy;

  public int vacc_coverage = 50;

  static byte SUSCEPTIBLE = 0;
  static byte VACCINATED = 1;
  static byte INFECTED = 2;
  static byte OVER_IT = 3;
  static BufferedImage[] person_images;
  int r0 = 5;
  int no_people = 200;

  int playfield_width;
  int playfield_height;
  float person_diam = 20;
  float two_person_diam = 2*person_diam;
  int person_radius = (int) Math.ceil(person_diam/2.0);
  float touch_distance = (float) Math.sqrt(2*person_diam*person_diam);
  int margin=2;
  BufferedImage playfield;
  Graphics2D pg2d;

  public void initGame() {
    no_vaccinations = (int) Math.round(0.01*vacc_coverage*no_people);
    int vacc=0;
    double mag;
    person_x = new float[no_people];
    person_y = new float[no_people];
    person_velx = new float[no_people];
    person_vely = new float[no_people];
    person_status = new byte[no_people];
    person_contacts = new byte[no_people];
    person_dups = new int[no_people][];
    person_infector = new int[no_people];
    person_infs = new int[no_people];
    no_healthy=no_people;
    for (int i=0; i<no_people; i++) {
      person_contacts[i] = (byte)poisson(r0);
      person_dups[i]=new int[person_contacts[i]];
      person_infector[i]=-1;
      boolean ok=false;
      while (!ok) {
        ok=true;
        person_x[i]=(float)(Math.random()*(playfield_width-(2.0f*person_diam)))+person_diam;
        person_y[i]=(float)(Math.random()*(playfield_height-(3.0f*person_diam)))+person_diam;
        for (int j=0; j<i-1; j++) {
          double dist=Math.sqrt(Math.pow(person_x[i]-person_x[j],2)+Math.pow(person_y[i]-person_y[j],2));
          if (dist<two_person_diam) ok=false;
        }
      }
      person_velx[i]=(float)(Math.random()-0.5f);
      person_vely[i]=(float)(Math.random()-0.5f);
      mag=1.5f/Math.sqrt((person_velx[i]*person_velx[i])+(person_vely[i]*person_vely[i]));
      person_velx[i]*=mag;
      person_vely[i]*=mag;
      if (i>=no_people-no_vaccinations) {
        person_status[i]=VACCINATED;
        vacc++;
        no_healthy--;
      }
      else person_status[i]=SUSCEPTIBLE;
    }
    no_vaccinations=vacc;
    pg2d.setColor(Color.BLACK);
    pg2d.fillRect(0,playfield_height,playfield_width,140);

    pg2d.setFont(littleStat);
    pg2d.setColor(Color.WHITE);

    FontMetrics fm = pg2d.getFontMetrics();

    sick_pos=(int)((playfield_width/2.0)+100);
    pg2d.drawString("SICK",sick_pos-(fm.stringWidth("SICK")/2),playfield_height+20);

    vacc_pos=(int)((playfield_width/2.0)-100);
    pg2d.drawString("VACCINATED",vacc_pos-(fm.stringWidth("VACCINATED")/2),playfield_height+20);

    recover_pos=(int)((playfield_width/2.0)+300);
    pg2d.drawString("RECOVERED",recover_pos-(fm.stringWidth("RECOVERED")/2),playfield_height+20);

    healthy_pos=(int)((playfield_width/2.0)-300);
    pg2d.drawString("HEALTHY",healthy_pos-(fm.stringWidth("HEALTHY")/2),playfield_height+20);
    if (job_name==null) job_name="";
    if (!job_name.trim().equals("")) pg2d.drawString("Name: "+job_name,20,playfield_height+20);


    pg2d.setFont(bigStat);
    fm = pg2d.getFontMetrics();
    pg2d.drawString(String.valueOf(no_vaccinations), vacc_pos-(fm.stringWidth(String.valueOf(no_vaccinations))/2),playfield_height+85);
  }

  public void contacts(int i, int j) {
    if (person_status[i]==INFECTED) {
      if (person_infector[i]!=j) {
        boolean ok=true;
        for (int k=0; k<person_contacts[i]; k++) {
          if (person_dups[i][k]==j) {
            ok=false;
            k=person_contacts[i];
          }
        }
        if (ok) {
          if (person_contacts[i]>0) {
            person_contacts[i]--;
            person_dups[i][person_contacts[i]]=j;
            if (person_status[j]==SUSCEPTIBLE) {
              person_infector[j]=i;
              person_infs[i]++;
              person_status[j]=INFECTED;
              no_infs++;
              no_healthy--;
            }
          }
          if (person_contacts[i]==0) {
            person_status[i]=OVER_IT;
            no_infs--;
            no_recoveries++;
          }
        }
      }
    }
  }


  public void movepeople() {
    float propose_x,propose_y;
    for (int i=0; i<no_people; i++) {
      propose_x=person_x[i]+person_velx[i];
      propose_y=person_y[i]+person_vely[i];
      if (propose_y>playfield_height-12) propose_y=playfield_height-12;

      // Check collisions...
      double dist;
      for (int j=0; j<no_people; j++) {
        if (i!=j) {
          int x_dist = (int) Math.abs(propose_x-person_x[j]);
          int y_dist = (int) Math.abs(propose_y-person_y[j]);
          if ((x_dist<person_diam) && (y_dist<person_diam)) {
            dist=Math.sqrt(Math.pow(propose_x-person_x[j],2)+Math.pow(propose_y-person_y[j],2));
            if (dist<touch_distance) {
              if (x_dist<=y_dist) person_velx[i]=-person_velx[i]; person_velx[j]=-person_velx[j];
              if (y_dist<=x_dist) person_vely[i]=-person_vely[i]; person_vely[j]=-person_vely[j];
              contacts(i,j);
              contacts(j,i);
            }
          }
        }
      }

      person_x[i]=person_x[i]+person_velx[i];
      person_y[i]=person_y[i]+person_vely[i];
      if (person_y[i]>playfield_height-12) person_y[i]=playfield_height-12;


      // Check playfield

      if ((person_x[i]-person_radius<=margin) && (person_velx[i]<0)) person_velx[i]=-person_velx[i];
      if ((person_x[i]+person_radius>=playfield_width-margin) && (person_velx[i]>0)) person_velx[i]=-person_velx[i];
      if ((person_y[i]-person_radius<=margin) && (person_vely[i]<0)) person_vely[i]=-person_vely[i];
      if ((person_y[i]+person_radius>=playfield_height-(5+margin)) && (person_vely[i]>0)) person_vely[i]=-person_vely[i];
    }
  }

  public void initPlayfield() {
    playfield = new BufferedImage(playfield_width,playfield_height+150,BufferedImage.TYPE_3BYTE_BGR);
    pg2d = (Graphics2D) playfield.getGraphics();
    pg2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    pg2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    pg2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    pg2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    pg2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    pg2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    pg2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 140);
    pg2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_NORMALIZE);
    scatter_marg=50;
    scatter_width=(int)((screen_width-(2.5*scatter_marg))/2);
    scatter_height=(screen_height-(2*scatter_marg));
    scatter = new BufferedImage(scatter_width,scatter_height,BufferedImage.TYPE_3BYTE_BGR);
    scatter2 = new BufferedImage(scatter_width,scatter_height,BufferedImage.TYPE_4BYTE_ABGR);
    bar = new BufferedImage(scatter_width,scatter_height,BufferedImage.TYPE_3BYTE_BGR);
    bar2 = new BufferedImage(scatter_width,scatter_height,BufferedImage.TYPE_4BYTE_ABGR);
    comp = new BufferedImage(scatter_width,scatter_height,BufferedImage.TYPE_3BYTE_BGR);
    comp2 = new BufferedImage(scatter_width,scatter_height,BufferedImage.TYPE_3BYTE_BGR);
    scatterG2 = (Graphics2D) scatter2.getGraphics();
    scatterG = (Graphics2D) scatter.getGraphics();
    barG = (Graphics2D) bar.getGraphics();
    barG2 = (Graphics2D) bar2.getGraphics();
    compG = (Graphics2D) comp.getGraphics();
    compG2 = (Graphics2D) comp2.getGraphics();
    scatterG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    scatterG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    scatterG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    scatterG.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    scatterG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    scatterG.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    scatterG.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 140);
    scatterG.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_NORMALIZE);

    barG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    barG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    barG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    barG.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    barG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    barG.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    barG.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 140);
    barG.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_NORMALIZE);

    compG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    compG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    compG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    compG.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    compG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    compG.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    compG.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, 140);
    compG.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_NORMALIZE);
    makeComparison();
  }

  public void drawPlayfield(float opac) {
    pg2d.setColor(Color.BLACK);
    pg2d.fillRect(0, 0, playfield_width, playfield_height+2);
    for (int i=0; i<no_people; i++) {
      pg2d.drawImage(person_images[person_status[i]],(int)person_x[i]-person_radius,(int)person_y[i]-person_radius,null);
    }
    if (opac<1.0f) {
      Color c = new Color(0,0,0,1.0f-opac);
      pg2d.setColor(c);
      pg2d.fillRect(0, 0, playfield_width, playfield_height+2);

    }

    if (prev_no_infs!=no_infs) {
      pg2d.setFont(bigStat);
      pg2d.setColor(Color.BLACK);
      pg2d.fillRect(sick_pos-45, playfield_height+45,90,85);
      pg2d.setColor(Color.WHITE);
      FontMetrics fm = pg2d.getFontMetrics();
      int pos=sick_pos-(fm.stringWidth(String.valueOf(no_infs))/2);
      pg2d.drawString(String.valueOf(no_infs),pos,playfield_height+85);
      prev_no_infs=no_infs;
    }

    if (prev_no_recoveries!=no_recoveries) {
      pg2d.setFont(bigStat);
      pg2d.setColor(Color.BLACK);
      pg2d.fillRect(recover_pos-45, playfield_height+45,90,85);
      pg2d.setColor(Color.WHITE);
      FontMetrics fm = pg2d.getFontMetrics();
      int pos=recover_pos-(fm.stringWidth(String.valueOf(no_recoveries))/2);
      pg2d.drawString(String.valueOf(no_recoveries),pos,playfield_height+85);
      prev_no_recoveries=no_recoveries;
    }

    if (prev_no_healthy!=(no_healthy+no_vaccinations)) {
      pg2d.setFont(bigStat);
      pg2d.setColor(Color.BLACK);
      pg2d.fillRect(healthy_pos-45, playfield_height+45,90,85);
      pg2d.setColor(Color.WHITE);
      FontMetrics fm = pg2d.getFontMetrics();
      int pos=healthy_pos-(fm.stringWidth(String.valueOf(no_healthy+no_vaccinations))/2);
      pg2d.drawString(String.valueOf(no_healthy+no_vaccinations),pos,playfield_height+85);
      prev_no_healthy=no_healthy+no_vaccinations;
    }


    g2d.drawImage(playfield,0,0,null);

  }
  public void go() {
    try {
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, new File("fonts/dotmatri-webfont.ttf")));
    } catch (Exception e) {}

    if (gui_mode==ANDROID) {
      gui_mode=ANDROID_IDLE;
      setStatus(STATUS_DEMO);
      nextCommand();
    }
    frame_timer.start();
    main.setVisible(true);
  }

  final static Color[] rainbow = new Color[] {new Color(255,32,32),new Color(255,128,0),new Color(255,255,0),new Color(64,255,64),new Color(0,192,255),new Color(128,96,255)};
  public void makeComparison() {
    compG.setColor(Color.BLACK);
    compG.fillRect(0, 0, scatter_width, scatter_height);
    compG.setColor(Color.WHITE);
    compG.setFont(graphBigTitle);
    compG.drawString("Vaccination effects for different R0 values",150,20);
    compG.drawLine((int)graph_margin, 0, (int)graph_margin,(int)(scatter_height-graph_margin));
    compG.drawLine((int)graph_margin,(int)(scatter_height-graph_margin),scatter_width,(int)(scatter_height-graph_margin));
    compG.setFont(graphNum);
    for (int i=0; i<10; i++) {
      compG.drawLine((int)(graph_margin-2),(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)),(int)graph_margin,(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)));
      compG.drawLine((int)(graph_margin+(i*(scatter_width-graph_margin)/10.0)),(int)(scatter_height-(graph_margin-2)),(int) (graph_margin+(i*(scatter_width-graph_margin)/10.0)),(int)(scatter_height-graph_margin));
      if (i>0) compG.drawString(String.valueOf(i*10),(int)(graph_margin-20),4+(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)));
      else compG.drawString(String.valueOf(i*10),(int)(graph_margin-13),4+(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)));
      if (i>0) compG.drawString(String.valueOf(i*10),(int)((graph_margin-6)+(i*(scatter_width-graph_margin)/10.0)),(int)(scatter_height-(graph_margin-18)));
      else compG.drawString(String.valueOf(i*10),(int)((graph_margin-3)+(i*(scatter_width-graph_margin)/10.0)),(int)(scatter_height-(graph_margin-18)));
    }
    compG.setFont(graphTitle);
    FontMetrics fm = compG.getFontMetrics();
    compG.drawString("Vaccinated People (%)",(int)(graph_margin+((scatter_width-graph_margin)/2)-(fm.stringWidth("Vaccinated People (%)")/2)),scatter_height-(int)(graph_margin-55));
    for (int r=3; r<=8; r++) {
      String fn = data_path+"data_"+String.valueOf(r)+".txt";
      try {
        if (!new File(fn).exists()) { PrintWriter PW = new PrintWriter(new File(fn)); PW.close(); }
        BufferedReader br = new BufferedReader(new FileReader(fn));
        String s = br.readLine();
        scatterG.setColor(Color.YELLOW);
        double y0=0;
        double grad=0;
        double x,y;
        double sumx=0,sumy=0,sumxy=0,sumxx=0;
        double n=0;
        while (s!=null) {
          String[] parts = s.split("\t");
          x=Integer.parseInt(parts[0]);
          y=Integer.parseInt(parts[1]);
          y/=(double)no_people;
          y*=100.0;
          sumx+=x;
          sumxx+=x*x;
          sumxy+=x*Math.log(y);
          sumy+=Math.log(y);
          n++;
          s = br.readLine();
        }
        br.close();
        grad=0;
        grad=(n*sumxy-(sumx*sumy))/((n*sumxx)-(sumx*sumx));
        y0=(sumy-(grad*sumx))/n;

        int lastY=0;
        compG.setColor(rainbow[r-3]);
        for (int i=(int)graph_margin; i<(int)(scatter_width); i++) {
          x=100.0*((i-graph_margin)/(scatter_width-graph_margin));
          y=Math.exp((grad*x)+y0);
          int ypix=(int)((scatter_height-graph_margin)-(((scatter_height-graph_margin)/100.0)*y));
          if (i>graph_margin) compG.drawLine(i-1,lastY,i,ypix);
          lastY=ypix;
        }
        compG.drawLine(400,200+(25*(r-3)),430,200+(25*(r-3)));
        compG.drawString(String.valueOf(r)+" people",450,208+(25*(r-3)));

      } catch (Exception e) { e.printStackTrace(); }
    }
    compG.setColor(Color.WHITE);
    compG.drawString("Average new infections caused (R0)",300,175);
    compG.rotate(-Math.PI/2.0);
    compG.drawString("Total Infections (%)",-150-(int)(((scatter_width-graph_margin)/2)-(fm.stringWidth("Total Infections (%)")/2)),15);
    compG.rotate(Math.PI/2.0);
  }

  public void updateLogs() {
    String fn = data_path+"data_"+String.valueOf(r0)+".txt";
    try {
      PrintWriter PW = new PrintWriter(new BufferedWriter(new FileWriter(fn, true)));
      PW.println(String.valueOf(vacc_coverage)+"\t"+String.valueOf(no_recoveries));
      PW.close();
    } catch (Exception e) { e.printStackTrace(); }

    scatterG.setColor(Color.BLACK);
    scatterG.fillRect(0, 0, scatter_width, scatter_height);
    scatterG.setColor(Color.WHITE);
    scatterG.setFont(graphBigTitle);
    scatterG.drawString("Results with average "+String.valueOf(r0)+" new infections caused",150,20);
    scatterG.drawLine((int)graph_margin, 0, (int)graph_margin,(int)(scatter_height-graph_margin));
    scatterG.drawLine((int)graph_margin,(int)(scatter_height-graph_margin),scatter_width,(int)(scatter_height-graph_margin));
    scatterG.setFont(graphNum);
    for (int i=0; i<10; i++) {
      scatterG.drawLine((int)(graph_margin-2),(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)),(int)graph_margin,(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)));
      scatterG.drawLine((int)(graph_margin+(i*(scatter_width-graph_margin)/10.0)),(int)(scatter_height-(graph_margin-2)),(int) (graph_margin+(i*(scatter_width-graph_margin)/10.0)),(int)(scatter_height-graph_margin));
      if (i>0) scatterG.drawString(String.valueOf(i*10),(int)(graph_margin-20),4+(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)));
      else scatterG.drawString(String.valueOf(i*10),(int)(graph_margin-13),4+(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)));
      if (i>0) scatterG.drawString(String.valueOf(i*10),(int)((graph_margin-6)+(i*(scatter_width-graph_margin)/10.0)),(int)(scatter_height-(graph_margin-18)));
      else scatterG.drawString(String.valueOf(i*10),(int)((graph_margin-3)+(i*(scatter_width-graph_margin)/10.0)),(int)(scatter_height-(graph_margin-18)));

    }
    scatterG.setFont(graphTitle);
    FontMetrics fm = scatterG.getFontMetrics();
    scatterG.drawString("Vaccinated People (%)",(int)(graph_margin+((scatter_width-graph_margin)/2)-(fm.stringWidth("Vaccinated People (%)")/2)),scatter_height-(int)(graph_margin-55));
    try {
      if (!new File(fn).exists()) { PrintWriter PW = new PrintWriter(new File(fn)); PW.close(); }
      BufferedReader br = new BufferedReader(new FileReader(fn));
      String s = br.readLine();
      scatterG.setColor(Color.YELLOW);
      double y0=0;
      double grad=0;
      double x,y;
      double sumx=0,sumy=0,sumxy=0,sumxx=0;
      double n=0;
      while (s!=null) {
        String[] parts = s.split("\t");
        x=Integer.parseInt(parts[0]);
        y=Integer.parseInt(parts[1]);
        y/=(double)no_people;
        y*=100.0;

        sumx+=x;
        sumxx+=x*x;
        sumxy+=x*Math.log(y);
        sumy+=Math.log(y);
        n++;
        x/=100.0;
        y/=100.0;
        scatterG.setColor(Color.PINK);
        scatterG.fillOval(-5+(int)(graph_margin+(scatter_width-graph_margin)*x),-5+(int)((scatter_height-graph_margin)-((scatter_height-graph_margin)*y)),10,10);
        scatterG.setColor(Color.YELLOW);
        scatterG.fillOval(-3+(int)(graph_margin+(scatter_width-graph_margin)*x),-3+(int)((scatter_height-graph_margin)-((scatter_height-graph_margin)*y)),6,6);
        s = br.readLine();
      }

      br.close();
      fn = data_path+"data_"+String.valueOf(r0)+"_names.txt";
      if (!new File(fn).exists()) {
        PrintWriter PW = new PrintWriter(new FileWriter(fn));
        if (job_name.trim().length()>0) {
          PW.println(job_name.trim()+"\t"+String.valueOf(vacc_coverage)+"\t"+String.valueOf(no_recoveries));
        }
        PW.close();
      } else {
        if (job_name.trim().length()>0) {
          String fn2 = data_path+"data_"+String.valueOf(r0)+"_names.new";
          PrintWriter PW = new PrintWriter(new FileWriter(fn2));
          PW.println(job_name.trim()+"\t"+String.valueOf(vacc_coverage)+"\t"+String.valueOf(no_recoveries));
          br = new BufferedReader(new FileReader(fn));
          s = br.readLine();
          if (s!=null) { if (s.trim().length()>1) PW.println(s); }
          s = br.readLine();
          if (s!=null) { if (s.trim().length()>1) PW.println(s); }
          br.close();
          PW.close();
          new File(fn).delete();
          new File(fn2).renameTo(new File(fn));
        }
      }

      br = new BufferedReader(new FileReader(fn));
      String[] names = new String[3];
      int[] vacc = new int[3];
      int[] no = new int[3];
      int no_names=0;
      s = br.readLine();
      while (s!=null) {
        String[] parts = s.split("\t");
        names[no_names]=new String(parts[0]);
        vacc[no_names]=Integer.parseInt(parts[1]);
        no[no_names]=Integer.parseInt(parts[2]);
        no_names++;
        s = br.readLine();
        if (s!=null)
          if (s.length()<3) s=null;
      }
      br.close();

      if (job_name.trim().length()==0) {
        x=vacc_coverage/100.0;
        y=(no_recoveries/(double)no_people);
        scatterG.setColor(Color.CYAN);
        scatterG.fillOval(-8+(int)(graph_margin+(scatter_width-graph_margin)*x),-8+(int)((scatter_height-graph_margin)-((scatter_height-graph_margin)*y)),16,16);
        scatterG.fillOval(300,100,16,16);
        scatterG.setColor(Color.RED);
        scatterG.fillOval(-6+(int)(graph_margin+(scatter_width-graph_margin)*x),-6+(int)((scatter_height-graph_margin)-((scatter_height-graph_margin)*y)),12,12);
        scatterG.fillOval(302,102,12,12);
        scatterG.setColor(Color.WHITE);
        scatterG.drawString("Latest result", 330, 116);
      }
      Color[] colors_rim = new Color[] {Color.GREEN,Color.BLUE,Color.RED};
      Color[] colors_mid = new Color[] {new Color(128,0,0),new Color(0,128,0),new Color(0,0,128)};

      for (int i=0; i<no_names; i++) {
        x=vacc[i]/100.0;
        y=no[i]/(double)no_people;
        int y_pos=130+(i*30);
        scatterG.setColor(colors_rim[i]);
        scatterG.fillOval(-8+(int)(graph_margin+(scatter_width-graph_margin)*x),-8+(int)((scatter_height-graph_margin)-((scatter_width-graph_margin)*y)),16,16);
        scatterG.fillOval(300,y_pos,16,16);
        scatterG.setColor(colors_mid[i]);
        scatterG.fillOval(-6+(int)(graph_margin+(scatter_width-graph_margin)*x),-6+(int)((scatter_height-graph_margin)-((scatter_width-graph_margin)*y)),12,12);
        scatterG.fillOval(302,y_pos+2,12,12);
        scatterG.setColor(Color.WHITE);
        scatterG.drawString(names[i], 330, y_pos+16);
      }

      grad=0;
      grad=(n*sumxy-(sumx*sumy))/((n*sumxx)-(sumx*sumx));
      y0=(sumy-(grad*sumx))/n;

      int lastY=0;
      scatterG.setColor(Color.RED);
      for (int i=(int)graph_margin; i<(int)(scatter_width); i++) {
        x=100.0*((i-graph_margin)/(scatter_width-graph_margin));
        y=Math.exp((grad*x)+y0);
        int ypix=(int)((scatter_height-graph_margin)-(((scatter_height-graph_margin)/100.0)*y));
        if (i>graph_margin) scatterG.drawLine(i-1,lastY,i,ypix);
        lastY=ypix;
      }
    } catch (Exception e) { e.printStackTrace(); }
    scatterG.setColor(Color.WHITE);
    scatterG.rotate(-Math.PI/2.0);
    scatterG.drawString("Total Infections (%)",-150-(int)(((scatter_width-graph_margin)/2)-(fm.stringWidth("Total Infections (%)")/2)),15);
    scatterG.rotate(Math.PI/2.0);

    // Now plot the bar graph as well.

    barG.setColor(Color.BLACK);
    barG.fillRect(0, 0, scatter_width, scatter_height);
    barG.setColor(Color.WHITE);
    barG.drawLine((int)graph_margin, 0, (int)graph_margin,(int)(scatter_height-graph_margin));
    barG.drawLine((int)graph_margin,(int)(scatter_height-graph_margin),scatter_width,(int)(scatter_height-graph_margin));
    barG.setFont(graphNum);
    fm = barG.getFontMetrics();
    int orig_no_people=no_people;
    no_people*=1.2;
    for (int i=0; i<10; i++) {
      barG.drawLine((int)(graph_margin-2),(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)),(int)graph_margin,(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)));
      if (i>0) barG.drawString(String.valueOf((int)(i*(no_people/10.0))),(int)(graph_margin-7)-fm.stringWidth(String.valueOf((i*(no_people/10.0)))),4+(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)));
      else barG.drawString(String.valueOf((int)(i*(no_people/10.0))),(int)(graph_margin-13),4+(int)((scatter_height-graph_margin)-(i*(scatter_height-graph_margin)/10.0)));
    }
    barG.setFont(graphTitle);

    //Things to plot...
    //1. Vaccinated people
    //2. People who were sick (recovered)
    //3. People who never even got contacted
    //4. Total healthy people

    int bar_width=(int)(((scatter_width-graph_margin)-2)/3.5);

    barG.setColor(Color.GREEN);
    int healthy_height=(int) (((no_healthy+no_vaccinations)/(double)no_people)*((scatter_height-graph_margin-2)));
    barG.fillRect((int)graph_margin+2,(int)(((scatter_height-graph_margin)-1)-healthy_height),bar_width,healthy_height);

    barG.setColor(Color.BLUE);
    int vacc_height=(int) ((no_vaccinations/(double)no_people)*((scatter_height-graph_margin-2)));
    barG.fillRect((int)graph_margin+2+bar_width,(int)(((scatter_height-graph_margin)-1)-vacc_height),bar_width,vacc_height);

    barG.setColor(Color.GRAY);
    int rec_height=(int) ((no_recoveries/(double)no_people)*((scatter_height-graph_margin-2)));
    barG.fillRect((int)graph_margin+2+(2*bar_width),(int)(((scatter_height-graph_margin)-1)-rec_height),bar_width,rec_height);
    no_people=orig_no_people;

    barG.setColor(Color.WHITE);
    barG.drawLine((int)(graph_margin+2),(int)(scatter_height-graph_margin),(int)(graph_margin+2),(int)(scatter_height-graph_margin)+2);
    barG.drawLine((int)(graph_margin+bar_width+2),(int)(scatter_height-graph_margin),(int)(graph_margin+bar_width+2),(int)(scatter_height-graph_margin)+2);
    barG.drawLine((int)(graph_margin+(2*bar_width)+2),(int)(scatter_height-graph_margin),(int)(graph_margin+(2*bar_width)+2),(int)(scatter_height-graph_margin)+2);
    barG.drawLine((int)(graph_margin+(3*bar_width)+2),(int)(scatter_height-graph_margin),(int)(graph_margin+(3*bar_width)+2),(int)(scatter_height-graph_margin)+2);
    barG.setFont(graphBigTitle);
    fm = barG.getFontMetrics();
    barG.drawString("Results for average "+String.valueOf(r0)+" new infections caused",150,20);
    barG.drawString("Vaccination coverage: "+String.valueOf(vacc_coverage)+" %",150,50);
    if ((job_name.trim().length()>0) && (!job_name.equals("DEMO"))) barG.drawString("Name: "+job_name,150,80);
    barG.setFont(graphNum);
    fm=barG.getFontMetrics();
    barG.drawString("Healthy",(int)(graph_margin+2+(bar_width/2)-(0.5*fm.stringWidth("Healthy"))),(int)(scatter_height-(graph_margin-18)));
    barG.drawString("Vaccinated",(int)(graph_margin+2+((3*bar_width)/2)-(0.5*fm.stringWidth("Vaccinated"))),(int)(scatter_height-(graph_margin-18)));
    barG.drawString("Were Infected",(int)(graph_margin+2+((5*bar_width)/2)-(0.5*fm.stringWidth("Were Infected"))),(int)(scatter_height-(graph_margin-18)));
    barG.setColor(Color.WHITE);
    barG.rotate(-Math.PI/2.0);
    barG.drawString("People",-150-(int)(((scatter_width-graph_margin)/2)-(fm.stringWidth("People")/2)),15);
    barG.rotate(Math.PI/2.0);
  } //g b r grey

  public Herd() {
    littleStat = new Font("Consolas",Font.BOLD,24);
    bigStat = new Font("Arial Narrow",Font.PLAIN,48);
    graphNum = new Font("Arial Narrow",Font.PLAIN,16);
    graphTitle = new Font("Arial Narrow",Font.PLAIN,20);
    graphBigTitle = new Font("Arial Narrow",Font.PLAIN,23);
    person_images = new BufferedImage[4];
    try {
      person_images[0] = ImageIO.read(new File("images/sus.gif"));
      person_images[1] = ImageIO.read(new File("images/vac.gif"));
      person_images[2] = ImageIO.read(new File("images/inf.gif"));
      person_images[3] = ImageIO.read(new File("images/imm.gif"));

    } catch (Exception e) {e.printStackTrace();}
    main = new JFrame();
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    screen_width = (int) screenSize.getWidth();
    screen_height = (int) screenSize.getHeight();

    playfield_width=screen_width;
    playfield_height=screen_height-150;
    main.setUndecorated(true);
    main.setSize(screen_width,screen_height);
    main.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    main.setLayout(new BorderLayout());
    initPlayfield();
    game = new JPanel();
    g2d = (Graphics2D) game.getGraphics();
    game.setPreferredSize(new Dimension(playfield_width,playfield_height));
    main.getContentPane().add(game,BorderLayout.CENTER);
    eh = new EventHandler();
    title = new TitlePanel(screen_width,screen_height,this);
    frame_timer = new Timer(5, eh);
    unattended_timer = new Timer(5000,eh);
    main.addMouseListener(eh);

  }

  public void seed(int no) {
    for (int i=0; i<no; i++) {
      person_status[i]=INFECTED;
      no_infs++;
      no_healthy--;
    }
  }

  public void setStatus(String s) {
    try {
      System.out.println(commandURL+"?cmd=set_status&msg="+s);
      URL url = new URL(commandURL+"?cmd=set_status&msg="+s);
      InputStream is = url.openStream();  // throws an IOException
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      br.readLine();
      br.close();
    } catch (Exception e) { e.printStackTrace(); }
  }

  public void nextCommand() {
    try {
      URL url = new URL(commandURL+"?cmd=next");
      InputStream is = url.openStream();  // throws an IOException
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      String s = br.readLine();
      br.close();
      if (!s.equals("EMPTY")) {
        String[] bits = s.split(",");
        int try_r0=Integer.parseInt(bits[0]);
        int try_vacc_coverage=Integer.parseInt(bits[1]);
        job_name="";
        if (bits.length>2) job_name=base64_decode(bits[2]);
        if ((try_r0==-1) && (try_vacc_coverage==-1)) {
          System.out.println("Entering demo-mode");
          job_name="";
          gui_mode=ANDROID_IDLE;
          current_mode=TITLE_PAGE;
          eh.extra_frames=0;
          setStatus(STATUS_DEMO);
          frame_timer.start();
        } else {
          gui_mode=ANDROID;
          setStatus(STATUS_RUNNING);
          r0=try_r0;
          vacc_coverage=try_vacc_coverage;
          current_mode=TITLE_PAGE;
          eh.extra_frames=0;
          frame_timer.start();
        }
      } else {
        if (gui_mode==ANDROID_IDLE) {
          nextRun();
          setStatus(STATUS_DEMO);
        }
      }

    } catch (Exception e) {
      System.out.println("Error accessing "+commandURL);
      e.printStackTrace();

    }
  }

  public void saveFrame() {
    f++;
    String movie="mov";
    if (f<1000) movie+="0";
    if (f<100) movie+="0";
    if (f<10) movie+="0";
    movie+=String.valueOf(f)+".png";
    try {
      ImageIO.write(playfield,"PNG",new File(movie));
    } catch (Exception ez) {}
  }

  public void testURL() {
    if (!commandURL.endsWith("/")) commandURL+="/";
    commandURL+="herdsim.php";
    try {
      URL url = new URL(commandURL+"?cmd=hello");
      InputStream is = url.openStream();  // throws an IOException
      BufferedReader br = new BufferedReader(new InputStreamReader(is));
      String s = br.readLine();
      br.close();
      System.out.println(commandURL+"?cmd=hello returned "+s);
      if (!(s.toUpperCase().equals("HERD_OK"))) {
        System.exit(-1);
      }
    } catch (Exception e) {
      System.out.println("Error accessing "+commandURL);
      e.printStackTrace();
      System.exit(-1);
    }
  }

  public static void main(String[] args) {
    String m="";//"/demo";
    if (args.length>0) m = args[0];
    final Herd h = new Herd();
    try {
      BufferedReader br = new BufferedReader(new FileReader("herd.ini"));
      String s = br.readLine();
      String[] bits = s.split("\t");
      h.current_r0_order=(byte)Integer.parseInt(bits[0]);
      h.current_vacc_order=(byte)Integer.parseInt(bits[1]);
      br.close();

    } catch (Exception e) {}

    h.gui_mode=PC;


    if (m.toLowerCase().equals("/demo")) {
      h.gui_mode=UNATTENDED;
      h.r0=h.r0_order[h.current_r0_order];
      h.vacc_coverage=h.vacc_order[h.current_vacc_order];
      h.current_mode=TITLE_PAGE;
    } else if (m.toLowerCase().equals("/url")) {
      h.commandURL=new String(args[1]);
      h.gui_mode=ANDROID;
      h.testURL();
    }

    SwingUtilities.invokeLater(new Runnable() {
      public void run(){
        h.go();
      }
    });

  }

  class EventHandler implements ActionListener, MouseListener {
    int extra_frames=0;
    int graph_counter=0;
    int graph_selection=0;
    int lock=0;
    public EventHandler() {}
    public void actionPerformed(ActionEvent e) {
      //saveFrame();
      if (e.getSource()==unattended_timer) {
        unattended_timer.stop();
        if (current_mode==TITLE_PAGE) {
          title.enable_buttons=false;
          current_mode=Herd.ENDING_TITLE;
          frame_timer.start();
        } else if (current_mode==BETWEEN_GAMES) {
          current_mode=TITLE_PAGE;
          extra_frames=0;
          frame_timer.start();
        }
      }
      if ((e.getSource()==frame_timer) && (lock>0)) System.out.println("Lock reentry");
      if ((e.getSource()==frame_timer) && (lock==0)) {
        lock++;
        if (Herd.this.current_mode==TITLE_PAGE) {
          extra_frames=0;
          title.startAnimation(screen_width,screen_height,pg2d);
          if (g2d==null) g2d = (Graphics2D) game.getGraphics();
          g2d.drawImage(playfield,0,0,null);
          frame_timer.stop();
          if ((gui_mode==UNATTENDED) || (gui_mode==ANDROID_IDLE) || (gui_mode==ANDROID)) {
            unattended_timer.start();
          }

        }

        else if (Herd.this.current_mode==ENDING_TITLE) {
          extra_frames++;
          title.titleFade((50.0f-extra_frames)/50.0f);
          if (g2d==null) g2d = (Graphics2D) game.getGraphics();
          g2d.drawImage(playfield,0,0,null);
          if (extra_frames>=50) {
            Herd.this.current_mode=STARTING_GAME;
            checkReset();
            initGame();
            no_infs=0;
            no_recoveries=0;
            prev_no_infs=-1;
            prev_no_recoveries=-1;
            extra_frames=0;
          }
        }

        else if (Herd.this.current_mode==STARTING_GAME) {
          extra_frames++;
          drawPlayfield(2.0f);
          movepeople();
          if (extra_frames==50) {
            seed(2);
            Herd.this.current_mode=PLAYING;
            extra_frames=0;
          }
        }

        else if (Herd.this.current_mode==PLAYING) {
          drawPlayfield(2.0f);
          movepeople();
          if (no_infs==0) {
            extra_frames++;
            if (extra_frames==50) {
              extra_frames=0;
              Herd.this.current_mode=ENDING_GAME;
            }
          }
        } else if (Herd.this.current_mode==ENDING_GAME){
          extra_frames++;

          if (extra_frames==START_WIGGLE_1) {
            for (int i=0; i<no_people; i++) {
              person_vely[i]=0;
              int target;
              if (person_status[i]==SUSCEPTIBLE) {
                target=healthy_pos+(int)(Math.random()*100)-50;
                person_velx[i]=(target-person_x[i])/(-1.0f+(float)(END_WIGGLE_1-START_WIGGLE_1));
                } else if (person_status[i]==VACCINATED) {
                target=vacc_pos+(int)(Math.random()*100)-50;
                person_velx[i]=(target-person_x[i])/(-1.0f+(float)(END_WIGGLE_1-START_WIGGLE_1));
              } else if (person_status[i]==OVER_IT) {
                target=recover_pos+(int)(Math.random()*100)-50;
                person_velx[i]=(target-person_x[i])/(-1.0f+(float)(END_WIGGLE_1-START_WIGGLE_1));
              }
            }
          } else if ((extra_frames>START_WIGGLE_1) && (extra_frames<END_WIGGLE_1)) {
            for (int i=0; i<no_people; i++) {
              person_x[i]+=person_velx[i];
            }
            drawPlayfield(2.0f);
          } else if (extra_frames==START_WIGGLE_2)  {
            int bottom = playfield_height-50;
            int range=playfield_height-100;
            int target;
            double scale=range/200.0;
            int ih=0;
            int iv=0;
            int ir=0;
            int all_healthy=no_healthy+no_vaccinations;
            for (int i=0; i<no_people; i++) {
              target=(int)(Math.random()*100);
              if (person_status[i]==SUSCEPTIBLE) {
                person_velx[i]=(((healthy_pos-50)+target)-person_x[i])/(1.0f+(float)(END_WIGGLE_2-START_WIGGLE_2));
                ih++;
                target=(int) (bottom-(5+((double)ih/(double)no_healthy)*(all_healthy*0.9)*scale));

              }
              else if (person_status[i]==VACCINATED) {
                person_velx[i]=(((vacc_pos-50)+target)-person_x[i])/(1.0f+(float)(END_WIGGLE_2-START_WIGGLE_2));
                iv++;
                target=(int) (bottom-(5+((double)iv/(double)no_vaccinations)*(no_vaccinations*0.9)*scale));

              }
              else if (person_status[i]==OVER_IT) {
                person_velx[i]=(((recover_pos-50)+target)-person_x[i])/(1.0f+(float)(END_WIGGLE_2-START_WIGGLE_2));
                ir++;
                target=(int) (bottom-(5+((double)ir/(double)no_recoveries)*(no_recoveries*0.9)*scale));
              }
              else target=0;
              person_vely[i]=(float)((target-person_y[i])/(1.0f+(float)(END_WIGGLE_2-START_WIGGLE_2)));
            }
          } else if ((extra_frames>START_WIGGLE_2) && (extra_frames<END_WIGGLE_2)) {
            for (int i=0; i<no_people; i++) {
              person_y[i]+=person_vely[i];
              person_x[i]+=person_velx[i];
              if (person_status[i]==SUSCEPTIBLE) {
                if (person_x[i]<healthy_pos-48) person_x[i]=healthy_pos-48;
                if (person_x[i]>healthy_pos+30) person_x[i]=healthy_pos+30;
              } else if (person_status[i]==VACCINATED) {
                if (person_x[i]<vacc_pos-48) person_x[i]=vacc_pos-48;
                if (person_x[i]>vacc_pos+30) person_x[i]=vacc_pos+30;
              } else if (person_status[i]==OVER_IT) {
                if (person_x[i]<recover_pos-48) person_x[i]=recover_pos-48;
                if (person_x[i]>recover_pos+30) person_x[i]=recover_pos+30;
              }
            }
            drawPlayfield(2.0f);

          } else if ((extra_frames>=START_BARS) && (extra_frames<END_BARS)) {
            if (extra_frames==220) {
              pg2d.setColor(Color.BLACK);
              pg2d.fillRect(0, 0, playfield_width, playfield_height);
            }
            int bottom = playfield_height-50;
            int range=playfield_height-100;
            double scale=range/200.0;
            Color c = new Color(0,1,0,(extra_frames-START_BARS)/((float)1.0*(END_BARS-START_BARS)));
            int all_healthy=no_healthy+no_vaccinations;
            pg2d.setColor(c);
            pg2d.fillRect(healthy_pos-60,(int)(bottom-(all_healthy*scale)), 120, 2+(int)(all_healthy*scale));

            c = new Color(0.4f,0.4f,0.4f,(extra_frames-START_BARS)/((float)1.0*(END_BARS-START_BARS)));
            pg2d.setColor(c);
            pg2d.fillRect(recover_pos-60,(int)(bottom-(no_recoveries*scale)), 120, 2+(int)(no_recoveries*scale));

            if (no_vaccinations>0) {
              c = new Color(0,0,1.0f,(extra_frames-START_BARS)/((float)1.0*(END_BARS-START_BARS)));
              pg2d.setColor(c);
              pg2d.fillRect(vacc_pos-60,(int)(bottom-(no_vaccinations*scale)), 120, 2+(int)(no_vaccinations*scale));
            }

            c = new Color(0,0,0,(extra_frames-START_BARS)/((float)1.0*(END_BARS-START_BARS)));
            pg2d.setColor(c);
            pg2d.fillRect(healthy_pos-60,(int)(bottom-(all_healthy*scale))-50,120,50);
            pg2d.fillRect(healthy_pos-60,bottom+1,120,10);
            pg2d.fillRect(recover_pos-60,(int)(bottom-(no_recoveries*scale))-50,120,50);
            pg2d.fillRect(recover_pos-60,bottom+1,120,10);
            pg2d.fillRect(vacc_pos-60,(int)(bottom-(no_vaccinations*scale))-50,120,50);
            pg2d.fillRect(vacc_pos-60,bottom+1,120,10);
            g2d.drawImage(playfield,0,0,null);


          } else if (extra_frames==END_BARS) {
            updateLogs();

          } else if (extra_frames==FADE_GRAPH) {
            pg2d.setColor(Color.BLACK);
            pg2d.fillRect(0,0,playfield_width,playfield_height+150);
            g2d.drawImage(playfield,0,0,null);

          } else if ((extra_frames>FADE_GRAPH) && (extra_frames<=END_FADE_GRAPH)) {
            scatterG2.drawImage(scatter, 0, 0, null);
            scatterG2.setColor(new Color(0,0,0,1.0f-(float)((extra_frames-FADE_GRAPH*1.0f)/(1.0f*END_FADE_GRAPH-FADE_GRAPH))));
            scatterG2.fillRect(0,0,scatter_width,scatter_width);
            barG2.drawImage(bar,0,0,null);
            barG2.setColor(new Color(0,0,0,1.0f-(float)((extra_frames-FADE_GRAPH*1.0f)/(1.0f*END_FADE_GRAPH-FADE_GRAPH))));
            barG2.fillRect(0,0,scatter_width,scatter_width);

            pg2d.drawImage(scatter2, (int)(1.25*scatter_marg)+scatter_width,(int)(scatter_marg*1),null);
            pg2d.drawImage(bar2,scatter_marg,(int)(scatter_marg*1),null);
            g2d.drawImage(playfield,0,0,null);
            if (extra_frames==END_FADE_GRAPH) {
              try {
                Thread.sleep(1000);
                //pg2d.drawImage(comp,scatter_marg,(int)(scatter_marg*1),null);
                //g2d.drawImage(playfield,0,0,null);
                //Thread.sleep(5000);

              } catch (Exception ex) {}
              frame_timer.start();
              graph_counter=0;
              graph_selection=1;

            }

          } else if (extra_frames>END_FADE_GRAPH) {
            extra_frames=END_FADE_GRAPH+1;
            if ((gui_mode==UNATTENDED) || (gui_mode==ANDROID_IDLE)) {
              if (gui_mode==ANDROID_IDLE) nextCommand();
              if (gui_mode==UNATTENDED) nextRun();
              frame_timer.stop();
              current_mode=BETWEEN_GAMES;
              unattended_timer.start();
            } else if (gui_mode==ANDROID) {
              graph_selection=1;
              setStatus(STATUS_WAITING);
              nextCommand();
            }
            if (current_mode==ENDING_GAME) {
              if (gui_mode==ANDROID) {
                try {Thread.sleep(100); } catch (Exception ex) {}
              } else {
                frame_timer.stop();
                pc_waiting_for_next_game=true;
              }

              /*graph_counter++;
              if (graph_counter>=50) {
                graph_counter=0;
                graph_selection++;
                if (graph_selection==2) {
                  pg2d.drawImage(bar2,scatter_marg,(int)(scatter_marg*1),null);
                  g2d.drawImage(playfield,0,0,null);

                } else if (graph_selection==3) {
                  pg2d.drawImage(comp,scatter_marg,(int)(scatter_marg*1),null);
                  g2d.drawImage(playfield,0,0,null);
                  graph_selection=1;
                }

              }*/
            }
          }
        }
        lock--;
      }

    }
    public void mouseReleased(MouseEvent e) {
      if ((title.enable_buttons) && (gui_mode==PC)) {
        title.clickButton(e.getX(), e.getY());
        if (g2d==null) g2d = (Graphics2D) game.getGraphics();
        g2d.drawImage(playfield,0,0,null);

      }

      else if (pc_waiting_for_next_game) {
        pc_waiting_for_next_game=false;
        current_mode=TITLE_PAGE;
        extra_frames=0;
        frame_timer.start();
        main.setVisible(true);

      }
    }
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
    public void mousePressed(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
  }
}
