/*
 *  sTermFX - A serial terminal application with some nifty features.
 *  Copyright (C) 2012  Brian Powell
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package stermfx;

import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.TooManyListenersException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.FadeTransition;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.util.Duration;
import javax.swing.Timer;
import stermfx.comms.CommPort;
import stermfx.comms.CommRxEvent;

/**
 *
 * @author Brian Powellx
 */
public class Terminal implements Initializable
{

    @FXML
    TextArea terminalTA;
    @FXML
    AnchorPane terminalAP;
    @FXML
    ToggleButton button;
    @FXML
    Accordion settings;
    @FXML
    ChoiceBox commPortCB;
    @FXML
    TextField baudRateTF;
    @FXML
    ToggleGroup dataBitsGroup = new ToggleGroup();
    @FXML
    ToggleGroup stopBitsGroup = new ToggleGroup();
    @FXML
    ChoiceBox parityCB;
    @FXML
    ChoiceBox flowControlCB;

    private Timer charTimer;
    private CommPort commPort;
    private volatile String lastTypedCharacter;
    private volatile boolean terminalBufferDirty;
    private Vector<Byte> terminalBuffer;
    private Properties sysSettings;
    private static final File SYS_SETTINGS_FILE = new File(System.getProperty("user.home") + File.separator
            + ".stermfx" + File.separator + "syssettings.properties");
    private static final String COMM_SETTINGS_FILENAME = System.getProperty("user.home") + File.separator
            + ".stermfx" + File.separator + "commsettings.properties";

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        // init globals
        terminalBuffer = new Vector<>();
        lastTypedCharacter = "";
        terminalBufferDirty = false;

        // create the receive event
        CommRxEvent rxEvent = new CommRxEvent()
        {

            @Override
            public void byteReceived(byte rxByte)
            {
                addCharacterToTerminal(rxByte);
            }
        };

        // create comm port object
        try
        {
            commPort = new CommPort(rxEvent, COMM_SETTINGS_FILENAME);
        }
        catch (IOException ex)
        {
            Logger.getLogger(Terminal.class.getName()).log(Level.SEVERE, null, ex);
        }

        // load the system settings
        loadSystemSettings();
        // init the UI controls
        initUI();

        try
        {
            commPort.commPortInterface().openCommPort(commPort);
            // only make the terminal edittable when the comm port is open
            terminalTA.setEditable(true);
        }
        catch (PortInUseException | IOException | TooManyListenersException | UnsupportedCommOperationException ex)
        {
            Logger.getLogger(Terminal.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void addCharacterToTerminal(byte character)
    {
        switch (character)
        {
            case 8:
                break;
//            case 13:
//                terminalBuffer += "<br>";
//                break;
            default:
                if (lastTypedCharacter.length() > 0)
                {
                    if (lastTypedCharacter.charAt(0) != (char) character)
                    {
                        // there's a problem so delete the textarea char and add this one
                        terminalTA.deletePreviousChar();
                        //terminalBuffer += (char)character;
                        terminalBuffer.add(character);
                        // only restart if this is first character since the timer has fired
                        if (!terminalBufferDirty)
                        {
                            charTimer.restart();
                        }
                        terminalBufferDirty = true;
                    }
                    lastTypedCharacter = "";
                }
                else
                {
                    terminalBuffer.add(character);
                    // only restart if this is first character since the timer has fired
                    if (!terminalBufferDirty)
                    {
                        charTimer.restart();
                    }
                    terminalBufferDirty = true;
                }
        }
    }

    private void caretAction()
    {
        // always service the terminal buffer first before dealing with the cursor
        if (terminalBufferDirty)
        {
            terminalBufferDirty = false;
            String tmp = "";
            while (terminalBuffer.size() > 0)
            {
                tmp += (char) terminalBuffer.remove(0).byteValue();
            }
            // update the text area and append a blank character to take care of scrolling
            terminalTA.appendText(tmp);
        }
    }

    private void loadSystemSettings()
    {
        try
        {
            // load the default and last saved settings
            Properties defaultProps = new Properties();
            defaultProps.load(getClass().getResourceAsStream("/stermfx/resources/sysdefaults.properties"));
            sysSettings = new Properties(defaultProps);
            if (SYS_SETTINGS_FILE.exists())
            {
                try (FileInputStream in = new FileInputStream(SYS_SETTINGS_FILE))
                {
                    sysSettings.load(in);
                }
            }
            // read the available system ports
            java.util.Enumeration<CommPortIdentifier> portEnum = CommPortIdentifier.getPortIdentifiers();
            // setup the selection values for the comm port name
            commPortCB.getItems().clear();
            while (portEnum.hasMoreElements())
            {
                CommPortIdentifier element = portEnum.nextElement();
                if (element.getPortType() == CommPortIdentifier.PORT_SERIAL)
                {
                    commPortCB.getItems().add(element.getName());
                }
            }
            commPortCB.getSelectionModel().select(commPort.getCommPortName());
            // the saved port may no longer be available on this system
            if (commPortCB.getSelectionModel().getSelectedIndex() < 0)
            {
                commPortCB.getSelectionModel().selectFirst();
                commPort.setCommPortName((String)commPortCB.getSelectionModel().getSelectedItem());
            }
            // set the baud rate
            baudRateTF.setText(commPort.getBaudRate());
            // find the toggle object that matches the current data bits settigstng
            for (Toggle current : dataBitsGroup.getToggles())
            {
                if (((ToggleButton)current).getText().equals(commPort.getDataBits()))
                {
                    dataBitsGroup.selectToggle(current);
                }
            }
            // find the toggle object that matches the current stop bits setting
            for (Toggle current : stopBitsGroup.getToggles())
            {
                if (((ToggleButton)current).getText().equals(commPort.getStopBits()))
                {
                    stopBitsGroup.selectToggle(current);
                }
            }
            // setup the parity choicebox settings
            parityCB.getItems().clear();
            parityCB.getItems().addAll("None", "Odd", "Even", "Mark", "Space");
            parityCB.getSelectionModel().select(commPort.getParity());
            // setup the flow control choicebox settings
            flowControlCB.getItems().clear();
            flowControlCB.getItems().addAll("None", "RtsCts In", "RtsCts Out", "XonXoff In", "XonXoff Out");
            flowControlCB.getSelectionModel().select(commPort.getFlowControl());
        }
        catch (IOException ioex)
        {
            Logger.getLogger(Terminal.class.getName()).log(Level.SEVERE, null, ioex);
        }
    }

    private void initUI()
    {
        // setup bindings for serial port settings UI controls
        commPort.commPortNameProperty().bindBidirectional(commPortCB.valueProperty());
        commPort.baudRateProperty().bindBidirectional(baudRateTF.textProperty());
        dataBitsGroup.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {

            @Override
            public void changed(ObservableValue<? extends Toggle> arg0, Toggle arg1, Toggle arg2)
            {
                commPort.setDataBits(((ToggleButton)arg2).getText());
                System.out.println(commPort);
            }
        });
        stopBitsGroup.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {

            @Override
            public void changed(ObservableValue<? extends Toggle> arg0, Toggle arg1, Toggle arg2)
            {
                commPort.setStopBits(((ToggleButton)arg2).getText());
            }
        });
        commPort.parityProperty().bindBidirectional(parityCB.valueProperty());
        commPort.flowControlProperty().bindBidirectional(flowControlCB.valueProperty());
        //commPort.dataBitsProperty().bindBidirectional(dataBitsGroup.selectedToggleProperty());

        // setup a timer regulate how fast characters are added to the text area
        // without this the runLater queue would get flooded
        charTimer = new Timer(500, new ActionListener()
        {

            @Override
            public void actionPerformed(java.awt.event.ActionEvent ae)
            {
                caretAction();
            }
        });
        charTimer.setRepeats(true);
        charTimer.setInitialDelay(100);
        charTimer.start();

        if (button != null)
        {
            button.setOnAction(new EventHandler<ActionEvent>()
            {

                @Override
                public void handle(ActionEvent event)
                {
                    if (terminalAP.getOpacity() == 1)
                    {
                        FadeTransition fadeOut = new FadeTransition(Duration.seconds(1), terminalAP);
                        fadeOut.setFromValue(1.0);
                        fadeOut.setToValue(0.0);
                        fadeOut.play();
                        terminalAP.setMouseTransparent(true);
                    }
                    else
                    {
                        FadeTransition fadeIn = new FadeTransition(Duration.seconds(1), terminalAP);
                        fadeIn.setFromValue(0.0);
                        fadeIn.setToValue(1.0);
                        fadeIn.play();
                        terminalAP.setMouseTransparent(false);
                    }
                }
            });
        }

        if (terminalTA != null)
        {
            terminalTA.setOnKeyTyped(new EventHandler<KeyEvent>()
            {

                @Override
                public void handle(KeyEvent arg0)
                {
                    //addCharacterToTerminal(arg0.getCharacter());
                    //System.out.println("Key: " + arg0.getCharacter().getBytes()[0]);
                    try
                    {
                        lastTypedCharacter = arg0.getCharacter();
                        commPort.commPortInterface().sendByte(lastTypedCharacter.getBytes()[0]);
                    }
                    catch (IOException ex)
                    {
                        Logger.getLogger(Terminal.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
//            terminalTA.setOnKeyReleased(new EventHandler<KeyEvent>() {
//                @Override
//                public void handle(KeyEvent arg0)
//                {
//                    System.out.println("Key: " + arg0.getCode());
//                }
//            });
        }
    }

    public void doCleanUp()
    {
        try
        {
            // close the comm port if open
            if (commPort.commPortInterface().isPortOpen())
            {
                commPort.commPortInterface().closeCommPort();
            }
        }
        catch (IOException ioex)
        {
            Logger.getLogger(Terminal.class.getName()).log(Level.SEVERE, null, ioex);
        }
        try
        {
            // create the system settings file if it doesn't exist
            if (!SYS_SETTINGS_FILE.exists())
            {
                SYS_SETTINGS_FILE.getParentFile().mkdirs();
                SYS_SETTINGS_FILE.createNewFile();
            }
            // store the system settings
            try (FileOutputStream out = new FileOutputStream(SYS_SETTINGS_FILE))
            {
                sysSettings.store(out, "---sTermFX Settings---");
            }
            // save the comm port settings
            commPort.saveSettings();
        }
        catch (IOException ioex)
        {
            Logger.getLogger(Terminal.class.getName()).log(Level.SEVERE, null, ioex);
        }
    }
}
