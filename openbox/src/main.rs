// TODO: most things are pretty hacky or not implemented at a standard I would like
// it to be but time + stress forces it sometimes (maybe laziness as well)

use iced::{
    alignment, color,
    widget::{button, center, column, container, focus_next, row, text, text_input},
    Center, Element, Subscription, Task, Theme,
};
use openbox::ws::Event;

const _PROD_URL: &str = "franklyn3.htl-leonding.ac.at:8080";
const _STAGING_URL: &str = "franklyn3a.htl-leonding.ac.at:8080";
const _CI_URL: &str = "franklyn.ddns.net:8080";
const _DEV_URL: &str = "localhost:8080";

const IS_VALID_RANGE: std::ops::RangeInclusive<usize> = 2..=50usize;

#[derive(Debug, Clone)]
enum Message {
    PinChanged(String),
    FirstnameChanged(String),
    LastnameChanged(String),

    Connect,
    ConnectKb,
    FocusNext,

    Ev(openbox::ws::Event),
}

enum State {
    Login,
    InvalidPin,

    Connected,
    Reconnecting,
}

struct Openbox<'a> {
    pin: String,
    firstname: String,
    lastname: String,

    server_address: &'a str,
    state: State,
}

impl<'a> Openbox<'a> {
    fn new() -> (Self, Task<Message>) {
        (
            Self {
                pin: String::new(),
                firstname: String::new(),
                lastname: String::new(),
                server_address: if cfg!(feature = "srv_prod") {
                    _PROD_URL
                } else if cfg!(feature = "srv_staging") {
                    _STAGING_URL
                } else if cfg!(feature = "srv_ci") {
                    _CI_URL
                } else {
                    _DEV_URL
                },
                state: State::Login,
            },
            Task::none(),
        )
    }

    fn is_input_valid(&self) -> bool {
        self.pin.len() == 3
            && self.pin.parse::<u16>().is_ok()
            && IS_VALID_RANGE.contains(&self.firstname.len())
            && IS_VALID_RANGE.contains(&self.lastname.len())
    }

    fn update(&mut self, message: Message) -> Task<Message> {
        match message {
            Message::PinChanged(pin) => self.pin = pin,
            Message::FirstnameChanged(firstname) => self.firstname = firstname,
            Message::LastnameChanged(lastname) => self.lastname = lastname,
            Message::Connect | Message::ConnectKb if self.is_input_valid() => self.state = State::Connected,
            Message::FocusNext => return focus_next(),
            Message::Ev(Event::Disconnect) => return iced::exit(),
            Message::Ev(Event::Connected) => self.state = State::Connected,
            Message::Ev(Event::Reconnect) => self.state = State::Reconnecting,
            Message::Ev(Event::ServerError) => self.state = State::InvalidPin,
            _ => (),
        }

        Task::none()
    }

    fn subscription(&self) -> Subscription<Message> {
        use iced::keyboard;
        use openbox::ws;

        let hotkeys = keyboard::on_key_press(|key, _| match key {
            keyboard::Key::Named(keyboard::key::Named::Tab) => Some(Message::FocusNext),
            keyboard::Key::Named(keyboard::key::Named::Enter) => Some(Message::ConnectKb),
            _ => None,
        });

        let ws = match self.state {
            State::Connected | State::Reconnecting => ws::subscribe(
                self.pin.clone(), 
                self.server_address.to_string(),
                self.firstname.clone(),
                self.lastname.clone(),
            ).map(Message::Ev),
            _ => Subscription::none()
        };

        Subscription::batch([hotkeys, ws])
    }

    fn view(&self) -> Element<Message> {
        center(match self.state {
            State::Login => self.login_view(),
            State::InvalidPin => self.invalid_pin_view(),
            State::Connected => self.connected_view(),
            State::Reconnecting => self.reconnection_view(),
        })
        .into()
    }

    fn logo_view(&self) -> Element<Message> {
        row![
            container(
                text("FRAN").size(70)).style(|_| openbox::theme::LogoTheme::default().to_style()
            ),
            text("KLYN").size(70),
        ]
            .align_y(Center)
            .into()
    }

    fn login_view(&self) -> Element<Message> {
        let pin_input = text_input("pin", &self.pin)
            .on_input(Message::PinChanged)
            .width(300)
            .padding(10);

        let firstname_input = text_input("firstname", &self.firstname)
            .on_input(Message::FirstnameChanged)
            .width(300)
            .padding(10);

        let lastname_input = text_input("lastname", &self.lastname)
            .on_input(Message::LastnameChanged)
            .width(300)
            .padding(10);

        let mut button = button(
            text("connect")
                .height(40)
                .align_y(alignment::Vertical::Center)
                .align_x(alignment::Horizontal::Center),
        )
        .width(300)
        .padding([0, 20]);

        if self.is_input_valid() {
            button = button.on_press(Message::Connect);
        }

        column![
            column![self.logo_view()].padding([50, 50]),
            pin_input,
            firstname_input,
            lastname_input,
            button
        ]
        .spacing(10)
        .align_x(Center)
        .into()
    }

    fn connected_view(&self) -> Element<Message> {
        column![
            self.logo_view(), 
            row![text(&self.firstname).size(50), text(&self.lastname).size(50)].spacing(20)
        ]
        .spacing(20)
        .align_x(Center)
        .into()
    }

    fn reconnection_view(&self) -> Element<Message> {
        column![
            self.connected_view(),
            text("Error: connection lost (trying to reconnect)")
                .size(20)
                .color(color!(0xFF0000))
        ]
        .spacing(20)
        .align_x(Center)
        .into()
    }

    fn invalid_pin_view(&self) -> Element<Message> {
        column![
            self.login_view(),
            text("Error: could not connect (is the pin correct?)")
                .size(20)
                .color(color!(0xFF0000))
        ]
        .spacing(20)
        .align_x(Center)
        .into()
    }

    fn theme(&self) -> Theme {
        Theme::default()
    }
}

pub fn main() -> iced::Result {
    iced::application("Openbox", Openbox::update, Openbox::view)
        .theme(Openbox::theme)
        .subscription(Openbox::subscription)
        .run_with(Openbox::new)
}
